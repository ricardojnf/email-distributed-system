package sd1920.trab1.arguments;

public class CreateFolderV2Args {

    private final String path;
    private final boolean autorename;

    public CreateFolderV2Args(String path){
        this.path = path;
        this.autorename = false;
    }
}
