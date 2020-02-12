/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) 2019 Thomas Niessen. All rights reserved.
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
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import chesspresso.position.FEN;
import chesspresso.position.PositionSupplier;

@SuppressWarnings("serial")
public class FenToClipBoard extends AbstractAction {
    private final PositionSupplier positionSupplier;
    private final ParentSupplier parentSupplier;

    public FenToClipBoard(PositionSupplier positionSupplier, ParentSupplier parentSupplier) {
	super("FEN");
	this.positionSupplier = positionSupplier;
	this.parentSupplier = parentSupplier;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
	String fen = FEN.getFEN(positionSupplier.getCurrentPosition());
	JDialog fenDialog;
	if (parentSupplier != null) {
	    Component parent = parentSupplier.getCurrentParent();
	    if (parent != null) {
		if (parent instanceof Frame) {
		    fenDialog = new JDialog((Frame) parent);
		} else if (parent instanceof Window) {
		    fenDialog = new JDialog((Window) parent);
		} else if (parent instanceof Dialog) {
		    fenDialog = new JDialog((Dialog) parent);
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
	if (parentSupplier != null && parentSupplier.getCurrentParent() != null) {
	    fenDialog.setLocationRelativeTo(parentSupplier.getCurrentParent());
	}
	fenDialog.setVisible(true);
    }

    public interface ParentSupplier {
	Component getCurrentParent();
    }
}
