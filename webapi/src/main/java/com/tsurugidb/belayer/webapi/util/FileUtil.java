package com.tsurugidb.belayer.webapi.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;

public class FileUtil {

    /**
     * get file size
     * 
     * @param path target file.
     * @return file size
     * @throws InternalServerErrorException Occurs when file size cannot be read
     */
    public static long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to read file size.", e);
        }
    }
    
}
