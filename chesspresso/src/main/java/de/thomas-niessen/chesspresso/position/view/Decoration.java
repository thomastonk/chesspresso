/*******************************************************************************
 * Copyright (C) 2019-2022Thomas Niessen. All rights reserved.
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
package chesspresso.position.view;

import java.awt.Color;
import java.awt.Graphics2D;

public interface Decoration {

	enum DecorationType {
		ARROW, BORDER, CIRCLE, CROSS_MARK, STROKE, FRAMED_AREA, GRAY_HAZE, TRIANGLE_IN_CORNER, ONE_IN_CORNER, ZERO_IN_CORNER,
		BAR_IN_CORNER, TEXT
	}

	void paint(Graphics2D g, int squareSize, int bottomPlayer);

	Color getColor();

	DecorationType getType();

}
