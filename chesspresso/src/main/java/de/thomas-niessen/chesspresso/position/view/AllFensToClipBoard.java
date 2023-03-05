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
package chesspresso.position.view;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import chesspresso.game.Game;

/**
 * @author Thomas Niessen
 */
@SuppressWarnings("serial")
public class AllFensToClipBoard extends AbstractAction {

	private final Supplier<Game> gameSupplier;
	private final Supplier<Component> parentSupplier;

	public AllFensToClipBoard(Supplier<Game> gameSupplier, Supplier<Component> parentSupplier) {
		super("All FENs");
		this.gameSupplier = gameSupplier;
		this.parentSupplier = parentSupplier;
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		Game game = gameSupplier.get();
		if (game == null) {
			JOptionPane.showMessageDialog(parentSupplier.get(), "No game available", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		Game copy = game.getDeepCopy();
		copy.gotoStart();
		StringBuilder sb = new StringBuilder();
		sb.append(copy.getPosition().getFEN());
		while (copy.goForward()) {
			sb.append(System.lineSeparator()).append(copy.getPosition().getFEN());
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
		JOptionPane.showMessageDialog(parentSupplier.get(), "All mainline FENs were copied to the system clipboard.", "All FENs",
				JOptionPane.INFORMATION_MESSAGE);
	}
}