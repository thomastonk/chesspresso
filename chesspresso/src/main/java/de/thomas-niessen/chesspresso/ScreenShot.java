package chesspresso;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class ScreenShot {

	// https://docs.oracle.com/javase/tutorial/2d/images/saveimage.html
	public static boolean saveScreenShot(Component c, String fileName) {
		BufferedImage bufferedImage = getScreenShot(c);
		try {
			File outputfile = new File(fileName);
			ImageIO.write(bufferedImage, "png", outputfile);
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}

	public static BufferedImage getScreenShot(Component c) {
		BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
		c.paint(image.getGraphics());
		return image;
	}

}
