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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import chesspresso.game.Game;
import chesspresso.pgn.PGNWriter;

/**
 * @author Thomas Niessen
 */
public class PgnToClipBoard implements ActionListener {

	private final Supplier<Game> gameSupplier;
	private final Supplier<Component> parentSupplier;

	public PgnToClipBoard(Supplier<Game> gameSupplier, Supplier<Component> parentSupplier) {
		this.gameSupplier = gameSupplier;
		this.parentSupplier = parentSupplier;
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		String s;
		Game game = gameSupplier.get();
		if (game == null) {
			JOptionPane.showMessageDialog(parentSupplier.get(),
					"Unable to generate PGN:" + System.lineSeparator() + "no game available", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		try {
			Game copy = game.getDeepCopy();
			copy.updateResult();
			s = PGNWriter.writeToString(copy);
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(parentSupplier.get(),
					"Unable to generate PGN:" + System.lineSeparator() + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		JDialog pgnDialog;
		Component parent = parentSupplier.get();
		if (parent != null) {
			if (parent instanceof Frame) {
				pgnDialog = new JDialog((Frame) parent);
			} else if (parent instanceof Window) {
				pgnDialog = new JDialog((Window) parent);
			} else if (parent instanceof Dialog) {
				pgnDialog = new JDialog((Dialog) parent);
			} else {
				pgnDialog = new JDialog();
			}
		} else {
			pgnDialog = new JDialog();
		}
		pgnDialog.setTitle("PGN");
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		JTextPane textPane = new JTextPane();
		textPane.setContentType("text/plain");
		textPanel.add(new JScrollPane(textPane));
		textPane.setText(s);
		textPane.setCaretPosition(0);
		textPane.setEditable(false);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		JButton copyButton = new JButton("Copy to clipboard");
		copyButton.addActionListener(f -> {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
			pgnDialog.setVisible(false);
			pgnDialog.dispose();
		});
		buttonPanel.add(copyButton);
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		textPanel.add(buttonPanel);
		pgnDialog.setModal(true);
		pgnDialog.add(textPanel);
		pgnDialog.pack();
		if (pgnDialog.getSize().width > 600) {
			pgnDialog.setSize(new Dimension(600, pgnDialog.getSize().height));
		}
		if (pgnDialog.getSize().height > 600) {
			pgnDialog.setSize(new Dimension(pgnDialog.getSize().width, 600));
		}
		// and only now:
		if (parent != null) {
			pgnDialog.setLocationRelativeTo(parent);
		}
		pgnDialog.setVisible(true);

	}

}
