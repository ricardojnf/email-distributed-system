package sd1920.trab1;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;
import sd1920.trab1.arguments.*;
import sd1920.trab1.replies.ListFolderReturn;
import sd1920.trab1.replies.ListFolderReturn.FolderEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DropBoxApi {

    //Max retries for a request, when the operation throws Error 429: too many requests
    private static final int MAX_RETRIES = 5;

    //DropBox API credentials
    private static final String apiKey = "wvijm7gmovut07q";
    private static final String apiSecret = "mglauhqbogr5je8";
    private static final String accessTokenStr = "-oIGe3vHhLAAAAAAAAAAC3dRDOR5zWn2piVZ_r1qVKPiDybuAS1Br1WH1vjdgfGs";

    //Character Encoding
    protected static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    protected static final String OCTET_STREAM_TYPE = "application/octet-stream";

    //DropBox API urls
    private static final String CREATE_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/create_folder_v2";
    private static final String CREATE_FOLDER_BATCH_URL = "https://api.dropboxapi.com/2/files/create_folder_batch";
    private static final String UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload";
    private static final String DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download";
    private static final String DELETE_V2_URL = "https://api.dropboxapi.com/2/files/delete_v2";
    private static final String LIST_FOLDER_URL = "https://api.dropboxapi.com/2/files/list_folder";
    private static final String LIST_FOLDER_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue";

    //OAuth
    private final OAuth20Service service;
    private final OAuth2AccessToken accessToken;

    //Google Gson
    private final Gson json;

    public DropBoxApi(String domain, boolean removeDomainFolder) {
        this.service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
        this.accessToken = new OAuth2AccessToken(accessTokenStr);
        this.json = new Gson();
        if (removeDomainFolder) {
            deleteFileOrFolder("/" + domain);
            createDirectory("/" + domain);
        }
    }

    public boolean uploadFile(String path, String mode, byte[] content) {
        OAuthRequest upload = new OAuthRequest(Verb.POST, UPLOAD_URL);
        upload.addHeader("Content-Type", OCTET_STREAM_TYPE);
        upload.addHeader("Dropbox-API-Arg", json.toJson(new UploadHeaderArgs(path, mode)));

        upload.setPayload(content);

        service.signRequest(accessToken, upload);

        return executeRequest(upload);
    }


    public boolean createDirectory(String path) {
        OAuthRequest createFolder = new OAuthRequest(Verb.POST, CREATE_FOLDER_V2_URL);
        createFolder.addHeader("Content-Type", JSON_CONTENT_TYPE);

        createFolder.setPayload(json.toJson(new CreateFolderV2Args(path)));

        service.signRequest(accessToken, createFolder);

        return executeRequest(createFolder);
    }

    public boolean createMultipleDirectories(List<String> paths) {
        OAuthRequest createMultipleFolders = new OAuthRequest(Verb.POST, CREATE_FOLDER_BATCH_URL);
        createMultipleFolders.addHeader("Content-Type", JSON_CONTENT_TYPE);

        createMultipleFolders.setPayload(json.toJson(new CreateFolderBatchArgs(paths)));

        service.signRequest(accessToken, createMultipleFolders);

        return executeRequest(createMultipleFolders);
    }

    public boolean deleteFileOrFolder(String path) {
        OAuthRequest delete = new OAuthRequest(Verb.POST, DELETE_V2_URL);
        delete.addHeader("Content-Type", JSON_CONTENT_TYPE);

        delete.setPayload(json.toJson(new DeleteV2Args(path)));

        service.signRequest(accessToken, delete);

        return executeRequest(delete);
    }

    public String downloadFile(String path) throws IOException {
        OAuthRequest download = new OAuthRequest(Verb.POST, DOWNLOAD_URL);
        download.addHeader("Content-Type", OCTET_STREAM_TYPE);
        download.addHeader("Dropbox-API-Arg", json.toJson(new DownloadHeaderArgs(path)));

        service.signRequest(accessToken, download);

        Response r;

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                r = service.execute(download);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            if (r.getCode() == 200) {
                return r.getBody(); //r.getHeader("dropbox-api-result");
            } else if (r.getCode() == 429 || r.getCode() == 500) {
                retries++;
                System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    //Do nothing
                }
            } else {
                System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
                try {
                    System.err.println(r.getBody());
                } catch (IOException e) {
                    System.err.println("No body in the response");
                }
                return null;
            }
        }
        return null;
    }

    public List<String> listFolder(String directoryName) {
        List<String> directoryContents = new ArrayList<>();

        OAuthRequest listDirectory = new OAuthRequest(Verb.POST, LIST_FOLDER_URL);
        listDirectory.addHeader("Content-Type", JSON_CONTENT_TYPE);
        listDirectory.setPayload(json.toJson(new ListFolderArgs(directoryName, false)));

        service.signRequest(accessToken, listDirectory);

        Response r = null;

        try {
            while (true) {

                int retries = 0;
                while (retries < MAX_RETRIES) {
                    r = service.execute(listDirectory);

                    if(r.getCode() == 200)
                        break;
                    else if (r.getCode() == 429 || r.getCode() == 500) {
                        System.err.println(r.getBody());
                        retries++;
                        Thread.sleep(5000);
                    } else {
                        System.err.println("Failed to list directory '" + directoryName + "'. Status " + r.getCode() + ": " + r.getMessage());
                        System.err.println(r.getBody());
                        return null;
                    }
                }

                ListFolderReturn reply = json.fromJson(r.getBody(), ListFolderReturn.class);

                for (FolderEntry e : reply.getEntries()) {
                    directoryContents.add(e.toString());
                }

                if (reply.has_more()) {
                    //There are more elements to read, prepare a new request (now a continuation)
                    listDirectory = new OAuthRequest(Verb.POST, LIST_FOLDER_CONTINUE_URL);
                    listDirectory.addHeader("Content-Type", JSON_CONTENT_TYPE);
                    //In this case the arguments is just an object containing the cursor that was returned in the previous reply.
                    listDirectory.setPayload(json.toJson(new ListFolderContinueArgs(reply.getCursor())));
                    service.signRequest(accessToken, listDirectory);
                } else {
                    break; //There are no more elements to read. Operation can terminate.
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return directoryContents;
    }

    private boolean executeRequest(OAuthRequest request) {

        Response r;

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                r = service.execute(request);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            if (r.getCode() == 200) {
                return true;
            } else if (r.getCode() == 429 || r.getCode() == 500) {
                retries++;
                System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    //Do nothing
                }
            } else {
                System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
                try {
                    System.err.println(r.getBody());
                } catch (IOException e) {
                    System.err.println("No body in the response");
                }
                return false;
            }
        }
        return false;
    }
}