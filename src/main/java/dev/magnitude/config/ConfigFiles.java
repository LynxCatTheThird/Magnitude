package dev.magnitude.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;

public final class ConfigFiles {
    private ConfigFiles(){}
    public static void write(Path path,String json){
        Path temporary=null;
        try {
            path=path.toAbsolutePath();Files.createDirectories(path.getParent());
            temporary=Files.createTempFile(path.getParent(),"magnitude-",".tmp");Files.writeString(temporary,json);
            if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try {Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException error){throw new IllegalStateException("Cannot save configuration",error);}
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
}
