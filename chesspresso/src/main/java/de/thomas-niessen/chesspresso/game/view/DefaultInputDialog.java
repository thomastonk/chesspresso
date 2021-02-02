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
package chesspresso.game.view;

import java.awt.Component;

import javax.swing.JOptionPane;

public class DefaultInputDialog implements InputDialog {

	DefaultInputDialog() {
	}

	@Override
	public String showInputDialog(Component parentComponent, String title, String message, String startInput) {
		Object obj = JOptionPane.showInputDialog(parentComponent, message, title, JOptionPane.OK_CANCEL_OPTION, null, null,
				startInput);
		if (obj != null && obj instanceof String) {
			return (String) obj;
		} else {
			return null;
		}
	}

}
