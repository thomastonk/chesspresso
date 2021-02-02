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
package chesspresso;

import java.awt.event.MouseEvent;

public class Mouse {

	public static boolean isSpecial(MouseEvent e) {
		// META excluded, because isMetaDown returns true, if right mouse button is
		// pressed.
		// POPUP_TRIGGER excluded, because isPopupTrigger returns false in mousePressed
		// and mouseDragged, but true in mouseReleased!
		return e.isAltDown() || e.isAltGraphDown() || e.isControlDown() || e.isShiftDown();
	}
}
