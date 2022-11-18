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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class AdvancedInputDialog implements InputDialog {

	private static ImageIcon icon = null;

	private final JLabel textAreaLabel;
	private final JTextArea textArea;
	private final JButton saveButton;
	private final JButton cancelButton;
	private boolean isCancelled;

	public AdvancedInputDialog() {
		textAreaLabel = new JLabel("Your text:");
		textArea = new JTextArea(5, 40);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		saveButton = new JButton("Save");
		cancelButton = new JButton("Cancel");
		isCancelled = false;
	}

	@Override
	public String showInputDialog(Component parentComponent, String title, String message, String startInput) {
		JDialog dialog = new JDialog();
		dialog.setModal(true);
		if (icon != null) {
			dialog.setIconImage(icon.getImage());
		}
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				isCancelled = true;
			}
		});

		if (title != null) {
			dialog.setTitle(title);
		} else {
			dialog.setTitle("Input dialog");
		}
		textAreaLabel.setText(Objects.requireNonNullElse(message, "Your input:"));
		textArea.setText(Objects.requireNonNullElse(startInput, ""));
		init(dialog);
		dialog.pack();
		dialog.setLocationRelativeTo(parentComponent);

		dialog.setVisible(true);

		// time for user action

		dialog.dispose();

		if (isCancelled) {
			return null;
		} else {
			String s = textArea.getText();
			if (s == null) {
				return "";
			} else {
				return s;
			}
		}
	}

	public void setTextAreaLabel(String text) {
		textAreaLabel.setText(Objects.requireNonNullElse(text, ""));
	}

	public void setSaveButton(String text) {
		saveButton.setText(Objects.requireNonNullElse(text, ""));
	}

	public void setCancelButton(String text) {
		cancelButton.setText(Objects.requireNonNullElse(text, ""));
	}

	private void init(JDialog dialog) {
		createLayout(dialog);
		addLogic(dialog);
	}

	private void createLayout(JDialog dialog) {

		JPanel allPanel = new JPanel();
		allPanel.setLayout(new BoxLayout(allPanel, BoxLayout.Y_AXIS));

		JPanel labelPanel = new JPanel();
		labelPanel.setMaximumSize(new Dimension(1000, 40));
		labelPanel.setLayout(new FlowLayout(FlowLayout.LEADING));

		labelPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

		labelPanel.add(textAreaLabel);
		allPanel.add(labelPanel);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		textPanel.add(new JScrollPane(textArea));
		allPanel.add(textPanel);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setMaximumSize(new Dimension(1000, 40));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		buttonPanel.add(saveButton);
		buttonPanel.add(Box.createRigidArea(new Dimension(10, 0)));
		buttonPanel.add(cancelButton);
		allPanel.add(buttonPanel);

		dialog.add(allPanel);
	}

	private void addLogic(JDialog dialog) {
		saveButton.addActionListener(e -> {
			isCancelled = false;
			dialog.setVisible(false);
		});
		saveButton.setEnabled(false);

		cancelButton.addActionListener(e -> {
			isCancelled = true;
			dialog.setVisible(false);
		});

		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				update();
			}

			private void update() {
				saveButton.setEnabled(true);
				cancelButton.setEnabled(true);
			}
		});
	}

	public static void setIcon(ImageIcon icon) {
		AdvancedInputDialog.icon = icon;
	}

}
