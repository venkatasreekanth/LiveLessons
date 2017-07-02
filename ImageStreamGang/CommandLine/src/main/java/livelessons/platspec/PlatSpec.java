package livelessons.platspec;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import livelessons.utils.Image;
import sun.rmi.runtime.Log;

import com.sun.jndi.toolkit.url.Uri;

import javax.imageio.ImageIO;

import static java.util.stream.Collectors.toList;

/**
 * A utility class containing static methods whose implementations are
 * specific to a particular platform (e.g., the Java platform vs. the
 * Android platform).  This implementation is specific for the Java
 * platform.
 */
public final class PlatSpec {
    /**
     * Logging tag.
     */
    private static final String TAG = PlatSpec.class.getName();

    /**
     * The path to the image directory.
     */
    private static final String IMAGE_DIRECTORY_PATH =
        "DownloadImages";

    /**
     * A utility class should always define a private constructor.
     */
    private PlatSpec() {
    }
    
    /**
     * Return the path to the external storage directory.
     */
    public static String getDirectoryPath() {
        return new File(IMAGE_DIRECTORY_PATH).getAbsolutePath();
    }

    /**
     * Creates an input stream for the passed URL. This method will
     * support both normal URLs and any URL located in the application
     * resources.
     *
     * @param url     Any URL including a resource URL.
     * @return An input stream.
     * @throws IOException
     */
    public static InputStream getInputStream(URL url)
            throws IOException {
       // Normal URL.
       return url.openStream();
    }

    /**
     * Write the @a image to the @a outputStream.
     */
    public static void writeImageFile(FileOutputStream outputStream,
                                      Image image) throws IOException {
        BufferedImage bufferedImage = image.getImage();
        if (bufferedImage == null)
            System.out.println("null image");
        else 
            ImageIO.write(bufferedImage,
                          "png",
                          outputStream);
    }

    /**
     * Gets the list of lists of URLs from which the user wants to
     * download images.
     */
    public static List<List<URL>> getUrlLists(Object c, Object l) {
        return null; // This is a no-op.
    }

    /**
     * Returns a List of default resource URL lists.
     */
    public static List<List<URL>> getDefaultResourceUrlList(Object ignore,
                                                            String[] defaultImageNames)
            throws MalformedURLException {
        return Stream
            // Convert the array of strings into a stream of strings.
            .of(defaultImageNames)

            // Map each string in the list into a list of URLs.
            .map(stringOfNames
                 -> {
                    return Pattern
                        // Create a regular expression for the "," separator.
                        .compile(",")

                        // Use regular expression to split stringOfNames into a
                        // Stream<String>.
                        .splitAsStream(stringOfNames)

                        // Concatenate the url prefix with each name.
                        .map(ClassLoader::getSystemResource)

                        // Create a list of URLs.
                        .collect(toList());
                 })

            // Create and return a list of a list of URLs.
            .collect(toList());
    }
}