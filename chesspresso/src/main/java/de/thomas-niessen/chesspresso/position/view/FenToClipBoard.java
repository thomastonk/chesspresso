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
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import chesspresso.position.FEN;
import chesspresso.position.Position;

/**
 * @author Thomas Niessen
 */
@SuppressWarnings("serial")
public class FenToClipBoard extends AbstractAction {
	private final Supplier<Position> positionSupplier;
	private final Supplier<Component> parentSupplier;

	public FenToClipBoard(Supplier<Position> positionSupplier, Supplier<Component> parentSupplier) {
		super("FEN");
		this.positionSupplier = positionSupplier;
		this.parentSupplier = parentSupplier;
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		String fen = FEN.getFEN(positionSupplier.get());
		JDialog fenDialog;
		if (parentSupplier != null) {
			Component parent = parentSupplier.get();
			if (parent != null) {
				if (parent instanceof Frame) {
					fenDialog = new JDialog((Frame) parent);
				} else if (parent instanceof Window) {
					fenDialog = new JDialog((Window) parent);
				} else {
					fenDialog = new JDialog();
				}
			} else {
				fenDialog = new JDialog();
			}
		} else {
			fenDialog = new JDialog();
		}
		fenDialog.setTitle("FEN");
		JPanel textPanel = new JPanel();
		JTextPane textPane = new JTextPane();
		textPane.setContentType("text/plain");
		textPanel.add(textPane);
		textPane.setText(fen);
		textPane.setEditable(false);
		JButton copyButton = new JButton("Copy to clipboard");
		copyButton.addActionListener(e -> {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fen), null);
			fenDialog.setVisible(false);
			fenDialog.dispose();
		});
		textPanel.add(copyButton);
		fenDialog.setModal(true);
		fenDialog.add(textPanel);
		fenDialog.pack();
		if (parentSupplier != null && parentSupplier.get() != null) {
			fenDialog.setLocationRelativeTo(parentSupplier.get());
		}
		fenDialog.setVisible(true);
	}
}
