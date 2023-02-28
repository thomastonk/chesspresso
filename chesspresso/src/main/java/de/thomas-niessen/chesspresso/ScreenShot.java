/*******************************************************************************
 * Copyright (C) 2020-2023 Thomas Niessen. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 ******************************************************************************/
package chesspresso;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public interface ScreenShot {

	/* This method creates a screenshot of the chessboard only. For other screenshots, 
	 * more methods need to be added. */
	boolean doBoardScreenShot(String fileName);

	/* The following methods are useful for abstract method. */

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
