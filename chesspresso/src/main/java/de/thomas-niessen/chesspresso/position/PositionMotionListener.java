/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) Thomas Niessen. All rights reserved.
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
package chesspresso.position;

import java.awt.event.MouseEvent;

public interface PositionMotionListener {
	// TN: The ImmutablePosition arguments are made for checking which position is meant.
	// A MoveablePosition could be used directly. Maybe some day a change can be made.

	boolean isDragAllowed(ImmutablePosition position, int from);

	void dragged(ImmutablePosition position, int from, int to, MouseEvent e);

	void squareClicked(ImmutablePosition position, int sqi, MouseEvent e);
}
