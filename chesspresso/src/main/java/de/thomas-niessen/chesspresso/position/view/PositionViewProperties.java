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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

import chesspresso.position.Position;

/**
 * @author Bernhard Seybold
 */
@SuppressWarnings("serial")
public class PositionViewProperties extends JDialog {

	private static class FontListRenderer extends BasicComboBoxRenderer {
		@Override
		@SuppressWarnings("rawtypes")
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			if (value instanceof Font) {
				return super.getListCellRendererComponent(list, ((Font) value).getName(), index, isSelected, cellHasFocus);
			} else {
				return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			}
		}
	}

	// ======================================================================

	private final PositionView m_positionView;

	public PositionViewProperties(Frame parent, boolean modal) {
		super(parent, modal);
		initComponents();

		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		m_positionView = new PositionView(Position.createInitialPosition());
		m_positionView.setShowCoordinates(true);
		panel.add(m_positionView);
		m_positionFrame.add(panel, BorderLayout.CENTER);

		Font font = m_positionView.getFont();
		teFontSize.setText(Integer.toString(font.getSize()));
		cbSolid.setSelected(m_positionView.getSolidStones());

		Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
		for (Font f : allFonts) {
			if (f.canDisplay(9812) && f.canDisplay(9818)) {
				cbFonts.addItem(f);
				cbFonts.setRenderer(new FontListRenderer());
				if (f.getName().equals(font.getName())) {
					cbFonts.setSelectedItem(f);
				}
			}
		}

		pack();
	}

	private void close() {
		setVisible(false);
		dispose();
	}

	// ======================================================================

	private void setFont() {
		try {
			Font font = (Font) cbFonts.getSelectedItem();
			if (font != null) {
				int fontSize = Integer.parseInt(teFontSize.getText());
				m_positionView.setFont(font.deriveFont(Font.PLAIN, fontSize));
			}
			m_positionView.setSolidStones(cbSolid.isSelected());
		} catch (NumberFormatException ignore) {
		}
	}

	public PositionView getPositionView() {
		return m_positionView;
	}

	// ======================================================================

	private void initComponents() {
		jPanel1 = new JPanel();
		jPanel6 = new JPanel();
		jPanel3 = new JPanel();
		butWhiteSquare = new JButton();
		butBlackSquare = new JButton();
		jPanel2 = new JPanel();
		butWhite = new JButton();
		butBlack = new JButton();
		jPanel4 = new JPanel();
		cbFonts = new JComboBox<>();
		teFontSize = new JTextField();
		cbSolid = new JCheckBox();
		m_positionFrame = new JPanel();

		setTitle("Position View Properties");
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent evt) {
				closeDialog(evt);
			}
		});

		jPanel1.setLayout(new BoxLayout(jPanel1, BoxLayout.Y_AXIS));

		jPanel6.setLayout(new BoxLayout(jPanel6, BoxLayout.X_AXIS));

		jPanel3.setBorder(BorderFactory.createTitledBorder("Square Color"));
		butWhiteSquare.setText("White");
		butWhiteSquare.addActionListener(this::butWhiteSquareActionPerformed);

		jPanel3.add(butWhiteSquare);

		butBlackSquare.setText("Black");
		butBlackSquare.addActionListener(this::butBlackSquareActionPerformed);

		jPanel3.add(butBlackSquare);

		jPanel6.add(jPanel3);

		jPanel2.setBorder(BorderFactory.createTitledBorder("Piece Color"));
		butWhite.setText("White");
		butWhite.addActionListener(this::butWhiteActionPerformed);

		jPanel2.add(butWhite);

		butBlack.setText("Black");
		butBlack.addActionListener(this::butBlackActionPerformed);

		jPanel2.add(butBlack);

		jPanel6.add(jPanel2);

		jPanel1.add(jPanel6);

		jPanel4.setBorder(BorderFactory.createTitledBorder("Font"));
		jPanel4.setToolTipText("null");
		cbFonts.addItemListener(this::cbFontsItemStateChanged);

		jPanel4.add(cbFonts);

		teFontSize.setText("12");
		teFontSize.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent evt) {
				teFontSizeKeyTyped(evt);
			}
		});

		jPanel4.add(teFontSize);

		cbSolid.setText("solid");
		cbSolid.addItemListener(this::cbSolidItemStateChanged);

		jPanel4.add(cbSolid);

		jPanel1.add(jPanel4);

		getContentPane().add(jPanel1, BorderLayout.NORTH);

		m_positionFrame.setLayout(new BorderLayout());

		getContentPane().add(m_positionFrame, BorderLayout.CENTER);

	}

	private void cbSolidItemStateChanged(ItemEvent evt) {
		setFont();
	}

	private void teFontSizeKeyTyped(KeyEvent evt) {
		setFont();
	}

	private void cbFontsItemStateChanged(ItemEvent evt) {
		setFont();
	}

	private void butBlackSquareActionPerformed(ActionEvent evt) {
		JColorChooser colorChooser = new JColorChooser(m_positionView.getBlackSquareColor());
		JDialog dialog = JColorChooser.createDialog(this, "Black Square Color", true, colorChooser, null, null);
		dialog.setVisible(true);
		m_positionView.setBlackSquareColor(colorChooser.getColor());
	}

	private void butWhiteSquareActionPerformed(ActionEvent evt) {
		JColorChooser colorChooser = new JColorChooser(m_positionView.getWhiteSquareColor());
		JDialog dialog = JColorChooser.createDialog(this, "White Square Color", true, colorChooser, null, null);
		dialog.setVisible(true);
		m_positionView.setWhiteSquareColor(colorChooser.getColor());
	}

	private void butBlackActionPerformed(ActionEvent evt) {
		JColorChooser colorChooser = new JColorChooser(m_positionView.getBlackColor());
		JDialog dialog = JColorChooser.createDialog(this, "Black Color", true, colorChooser, null, null);
		dialog.setVisible(true);
		m_positionView.setBlackColor(colorChooser.getColor());
	}

	private void butWhiteActionPerformed(ActionEvent evt) {
		JColorChooser colorChooser = new JColorChooser(m_positionView.getWhiteColor());
		JDialog dialog = JColorChooser.createDialog(this, "White Color", true, colorChooser, null, null);
		dialog.setVisible(true);
		m_positionView.setWhiteColor(colorChooser.getColor());
	}

	private void closeDialog(WindowEvent evt) {
		close();
	}

	// Variables declaration - do not modify
	private JPanel jPanel4;
	private JPanel jPanel3;
	private JButton butBlackSquare;
	private JButton butBlack;
	private JButton butWhite;
	private JPanel jPanel2;
	private JButton butWhiteSquare;
	private JComboBox<Font> cbFonts;
	private JPanel m_positionFrame;
	private JPanel jPanel1;
	private JPanel jPanel6;
	private JCheckBox cbSolid;
	private JTextField teFontSize;
	// End of variables declaration

}
