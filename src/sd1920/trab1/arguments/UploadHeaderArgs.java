package sd1920.trab1.arguments;

public class UploadHeaderArgs {

    private final String path, mode;
    private final boolean autorename, mute, strict_conflict;

    public UploadHeaderArgs(String path, String mode){
        this.path = path;
        this.mode = mode;
        this.autorename = false;
        this.mute = true;
        this.strict_conflict = false;
    }
}
