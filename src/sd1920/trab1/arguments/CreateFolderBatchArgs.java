package sd1920.trab1.arguments;

import java.util.List;

public class CreateFolderBatchArgs {

    private final List<String> paths;
    private final boolean autorename, force_async;

    public CreateFolderBatchArgs(List<String> paths){
        this.paths = paths;
        this.autorename = false;
        this.force_async = false;
    }
}
