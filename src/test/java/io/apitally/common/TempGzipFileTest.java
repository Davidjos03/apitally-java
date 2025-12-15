package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TempGzipFileTest {
    private TempGzipFile file;

    @BeforeEach
    void setUp() throws IOException {
        file = new TempGzipFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (file != null) {
            file.close();
            file.delete();
        }
    }

    @Test
    void testEndToEnd() throws IOException {
        assertTrue(file.getUuid().toString().length() == 36);
        assertTrue(file.getPath().toFile().exists());
        assertEquals(0, file.getSize());

        file.writeLine("test1".getBytes());
        file.writeLine("test2".getBytes());
        assertTrue(file.getSize() > 0);

        file.close();

        try (InputStream fileInputStream = file.getInputStream();
                GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            String content = outputStream.toString();
            assertEquals("test1\ntest2\n", content);
        }
    }
}
