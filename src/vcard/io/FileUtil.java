package vcard.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.util.Log;

/**
 * File utilities
 * 
 * @author francisdb
 *
 */
public final class FileUtil {
	
	private static final String TAG = FileUtil.class.getSimpleName();
	
	private FileUtil() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Read a file to string
	 * 
	 * @param file the file
	 * 
	 * @return the file contents as string
	 */
	public static final String readFile(final File file) throws IOException{
		StringBuilder builder = new StringBuilder();
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			BufferedReader buf = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while ((line = buf.readLine()) != null) {
				builder.append(line).append("\n");
			}
			buf.close();
		} finally {
			close(is);
		}
		return builder.toString();
	}
	
	/**
	 * Closes a Closeable and logs eventiual exceptions
	 * 
	 * @param closeable (can be <code>null</code>)
	 */
	public static void close(final Closeable closeable){
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}
}
