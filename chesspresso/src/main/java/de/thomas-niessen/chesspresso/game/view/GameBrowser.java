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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.game.GameModelChangeListener;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.FEN;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.OneClickMove;
import chesspresso.position.PositionListener;
import chesspresso.position.PositionMotionListener;
import chesspresso.position.view.Decoration.DecorationType;
import chesspresso.position.view.DecorationFactory;
import chesspresso.position.view.FenToClipBoard;
import chesspresso.position.view.PgnToClipBoard;
import chesspresso.position.view.PositionView;
import chesspresso.position.view.PositionViewProperties;

/**
 * Game browser.
 *
 * @author Bernhard Seybold
 */
@SuppressWarnings("serial")
public class GameBrowser extends JPanel implements PositionMotionListener, PositionListener, GameModelChangeListener {

	private Game m_game;
	private PositionView m_positionView;
	private GameTextViewer m_textViewer;
	private boolean m_editable;
	private boolean m_oneClickMoves;

	private Component m_parent = null;
	private JLabel m_moveLabel = null;

	private boolean m_highlightLastMove;

	// ======================================================================

	/**
	 * Create a new game browser.
	 *
	 * @param game the game to be displayed
	 */
	public GameBrowser(Game game) {
		this(game, Chess.WHITE, false, false);
	}

	/**
	 * Create a new game browser.
	 *
	 * @param game            the game to be displayed
	 * @param boardOnTheRight instead board on the left-hand side
	 */
	public GameBrowser(Game game, boolean boardOnTheRight) {
		this(game, Chess.WHITE, false, boardOnTheRight);
	}

	/**
	 * Create a new game browser.
	 *
	 * @param game            the game to be displayed
	 * @param bottomPlayer    the player on the lower edge
	 * @param boardOnTheRight instead board on the left-hand side
	 */
	public GameBrowser(Game game, int bottomPlayer, boolean boardOnTheRight) {
		this(game, bottomPlayer, false, boardOnTheRight);
	}

	/**
	 * Create a new game browser.
	 *
	 * @param game            the game to be displayed
	 * @param bottomPlayer    the player on the lower edge
	 * @param editable        whether the game can be edited by the view
	 * @param boardOnTheRight instead board on the left-hand side
	 */
	public GameBrowser(Game game, int bottomPlayer, boolean editable, boolean boardOnTheRight) {
		super();
		initComponents(boardOnTheRight);
		setGame(game, bottomPlayer);

		m_positionView.setShowSqiEP(false);
		m_positionView.setFocusable(false);
		m_positionFrame.add(m_positionView, BorderLayout.CENTER);

		m_textFrame.add(new JScrollPane(m_textViewer), BorderLayout.CENTER);

		m_editable = editable;
		m_oneClickMoves = false;
		addPopupToPositionView();
		addPopupToTextViewer();

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent componentEvent) {
				if (boardOnTheRight) {
					setDividerLocation(getSize().width - getDividerSize() - 8 * m_positionView.getSquareSize());
				} else {
					setDividerLocation(Math.min(getSize().width, 8 * m_positionView.getSquareSize()));
				}
			}
		});

		// TN: guarantees a suitable maximum height
		setMaximumSize(new Dimension(2000, m_positionView.getPreferredSize().height + 100));
		// The value 100 is larger than the height of the two header lines plus the
		// height of the tools under the position view. Other values work, too, but too
		// small values, say 30 cause problems.
	}

	// ======================================================================

	public void setGame(Game game, int bottomPlayer) {
		if (game != null) {
			if (m_game != null) {
				m_game.getPosition().removePositionListener(this);
				m_game.removeChangeListener(this);
			}
			m_game = game;
			m_game.gotoStart();
			m_game.getPosition().addPositionListener(this);
			m_game.addChangeListener(this);

			if (m_positionView == null) {
				m_positionView = new PositionView(m_game.getPosition(), bottomPlayer);
			} else {
				m_positionView.setPosition(m_game.getPosition());
				m_positionView.setBottomPlayer(bottomPlayer);
			}
			m_positionView.setPositionMotionListener(this);

			if (m_textViewer == null) {
				m_textViewer = new GameTextViewer(m_game);
			} else {
				m_textViewer.setGame(game);
			}

			setHeaderLines();
			highlightLastMove();
			m_textViewer.showCurrentGameNode();
		}
	}

	// ======================================================================

	public void setProperties(PositionViewProperties props) {
		m_positionView.setProperties(props);
		invalidate();
	}

	// ======================================================================

	public void setProperties(final Font f, final Color whiteSquares, final Color blackSquares) {
		if (m_positionView != null) {
			m_positionView.setFont(f);
			m_positionView.setWhiteSquareColor(whiteSquares);
			m_positionView.setBlackSquareColor(blackSquares);
			invalidate();
		}
	}

	@Override
	public void setFont(final Font f) {
		if (m_positionView != null) {
			m_positionView.setFont(f);
			invalidate();
		}
	}

	@Override
	public Font getFont() {
		if (m_positionView != null) {
			return m_positionView.getFont();
		} else {
			return null;
		}
	}

	public void setWhiteSquareColor(final Color whiteSquares) {
		if (m_positionView != null) {
			m_positionView.setWhiteSquareColor(whiteSquares);
			invalidate();
		}
	}

	public void setBlackSquareColor(final Color blackSquares) {
		if (m_positionView != null) {
			m_positionView.setBlackSquareColor(blackSquares);
			invalidate();
		}
	}

	public void setHighlightLastMove(boolean hlm) {
		m_highlightLastMove = hlm;
		highlightLastMove();
	}

	public boolean isHighlightingLastMove() {
		return m_highlightLastMove;
	}

	public Game getGame() {
		return m_game;
	}

	public String getCurrentPositionAsFEN() {
		return FEN.getFEN(m_game.getPosition());
	}

	public Font getPositionViewFont() {
		if (m_positionView != null) {
			return m_positionView.getFont();
		} else {
			return null;
		}
	}

	public Color getWhiteSquareColor() {
		if (m_positionView != null) {
			return m_positionView.getWhiteSquareColor();
		} else {
			return null;
		}
	}

	public Color getBlackSquareColor() {
		if (m_positionView != null) {
			return m_positionView.getBlackSquareColor();
		} else {
			return null;
		}
	}

	public PositionView getPositionView() {
		return m_positionView;
	}

	public JPanel getToolbar() {
		return toolBarPanel;
	}

	public void highlightFirstHeaderLine(boolean highlight) {
		m_lbHeader0.setOpaque(highlight);
	}

	public void highlightSecondHeaderLine(boolean highlight) {
		m_lbHeader1.setOpaque(highlight);
	}

	public void setGameTextFocusRequesting(boolean request) {
		m_textViewer.setFocusRequesting(request);
	}

	public void allowOneClickMoves(boolean allow) {
		m_oneClickMoves = allow;
	}

	// ======================================================================
	// Methods to implement PositionMotionListener

	@Override
	public boolean isDragAllowed(ImmutablePosition position, int from) {
		// allow dragging only if editable and there is a stone on the square
		return m_editable && m_game.getPosition().getStone(from) != Chess.NO_STONE;
	}

	@Override
	public boolean dragged(ImmutablePosition position, int from, int to, MouseEvent e) {
		try {
			m_game.getPosition().doMove(m_game.getPosition().getMove(from, to, Chess.NO_PIECE));
			// TN: this code is not correct, because no promotion is possible.
		} catch (IllegalMoveException ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public void squareClicked(ImmutablePosition position, int sqi, MouseEvent e) {
		if (m_editable && m_oneClickMoves && m_game.getPosition() == position) {
			OneClickMove.squareClicked(m_game.getPosition(), sqi, e);
		}
	}

	// ======================================================================

	public int getBottomPlayer() {
		return m_positionView.getBottomPlayer();
	}

	public void setBottomPlayer(int player) {
		m_positionView.setBottomPlayer(player);
	}

	public boolean getEditable() {
		return m_editable;
	}

	public void setEditable(boolean editable) {
		m_editable = editable;
	}

	public void setParent(final Component c) {
		m_parent = c;
	}

	// =======================================================================

	public void highlightLastMove() {
		if (m_positionView == null) {
			return;
		}
		m_positionView.removeDecorations(DecorationType.ARROW, Color.BLUE);
		if (m_highlightLastMove) {
			Move lastMove = m_game.getLastMove();
			if (lastMove != null) {
				if (!lastMove.isCastle() && !lastMove.isCastleChess960()) {
					m_positionView.addDecoration(
							DecorationFactory.getArrowDecoration(lastMove.getFromSqi(), lastMove.getToSqi(), Color.BLUE), false);
				} else {
					int fromSquare, toSquare;
					if (lastMove.isWhiteMove()) {
						if (lastMove.isShortCastle() || lastMove.isShortCastleChess960()) {
							fromSquare = Chess.F1;
							toSquare = Chess.G1;
						} else {
							fromSquare = Chess.C1;
							toSquare = Chess.D1;
						}
					} else {
						if (lastMove.isShortCastle() || lastMove.isShortCastleChess960()) {
							fromSquare = Chess.F8;
							toSquare = Chess.G8;
						} else {
							fromSquare = Chess.C8;
							toSquare = Chess.D8;
						}
					}
					m_positionView.addDecoration(DecorationFactory.getArrowDecoration(fromSquare, toSquare, Color.BLUE), false);
					m_positionView.addDecoration(DecorationFactory.getArrowDecoration(toSquare, fromSquare, Color.BLUE), false);
				}
			}
		}
		m_positionView.repaint();
	}

	// =======================================================================

	public void flip() {
		m_positionView.flip();
	}

	// =======================================================================
	private void initComponents(boolean boardOnTheRight) {
		jPanel1 = new javax.swing.JPanel();
		jPanel2 = new javax.swing.JPanel();
		jPanel3 = new javax.swing.JPanel();
		m_lbHeader0 = new javax.swing.JLabel();
		m_lbHeader0.setBackground(Color.ORANGE);
		m_lbHeader1 = new javax.swing.JLabel();
		m_lbHeader1.setBackground(Color.ORANGE);
		jSplitPane1 = new javax.swing.JSplitPane();
		m_positionFrame = new javax.swing.JPanel();
		m_textFrame = new javax.swing.JPanel();
		toolBarPanel = new javax.swing.JPanel();
		jToolBar1 = new javax.swing.JToolBar();
		jToolBar1.setFloatable(false);
		jToolBar2 = new javax.swing.JToolBar();
		jToolBar2.setFloatable(false);
		m_buttFlip = new javax.swing.JButton();
		m_buttStart = new javax.swing.JButton();
		m_buttBackward = new javax.swing.JButton();
		m_buttForward = new javax.swing.JButton();
		m_buttEnd = new javax.swing.JButton();

		setLayout(new java.awt.BorderLayout());

		toolBarPanel.setLayout(new java.awt.BorderLayout());

		jPanel1.setLayout(new BorderLayout());
		jPanel2.setLayout(new javax.swing.BoxLayout(jPanel2, javax.swing.BoxLayout.Y_AXIS));
		jPanel3.setLayout(new javax.swing.BoxLayout(jPanel3, javax.swing.BoxLayout.Y_AXIS));
		jPanel1.add(jPanel2, BorderLayout.CENTER);
		jPanel1.add(jPanel3, BorderLayout.EAST);
		jPanel1.setFocusable(false);

		m_lbHeader0.setText("0");
		jPanel2.add(m_lbHeader0);

		m_lbHeader1.setText("1");
		jPanel2.add(m_lbHeader1);

		m_lbHeader0.setAlignmentX(CENTER_ALIGNMENT);
		m_lbHeader1.setAlignmentX(CENTER_ALIGNMENT);

		add(jPanel1, java.awt.BorderLayout.NORTH);

		m_positionFrame.setLayout(new javax.swing.BoxLayout(m_positionFrame, javax.swing.BoxLayout.X_AXIS));

		JPanel posPanel = new JPanel();
		posPanel.setLayout(new javax.swing.BoxLayout(posPanel, javax.swing.BoxLayout.Y_AXIS));
		posPanel.add(m_positionFrame);
		if (!boardOnTheRight) {
			jSplitPane1.setLeftComponent(posPanel);
		} else {
			jSplitPane1.setRightComponent(posPanel);
		}

		m_textFrame.setLayout(new java.awt.BorderLayout());

		m_textFrame.setMinimumSize(new java.awt.Dimension(256, 128));
		m_textFrame.setPreferredSize(new java.awt.Dimension(256, 256));
		// m_textFrame.setPreferredSize(new java.awt.Dimension(330, 256));
		// TN: Generally the PositionView should take its required size and not more.
		// And the GameTextViewer should have the remaining space in the component.
		// But this does not happen, if the GameTextViewer is on the left. Here 330 is
		// somehow a magic number. The handling should be improved.
		if (!boardOnTheRight) {
			jSplitPane1.setRightComponent(m_textFrame);
		} else {
			jSplitPane1.setLeftComponent(m_textFrame);
		}

		add(jSplitPane1, java.awt.BorderLayout.CENTER);

		m_buttFlip.setText("^");
		m_buttFlip.setToolTipText("Flip");
		m_buttFlip.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				m_buttFlipActionPerformed(evt);
			}
		});

		jToolBar1.add(m_buttFlip);

		m_buttStart.setText("|<");
		m_buttStart.setToolTipText("Start");
		m_buttStart.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				m_buttStartActionPerformed(evt);
			}
		});

		jToolBar1.add(m_buttStart);

		m_buttBackward.setText("<");
		m_buttBackward.setToolTipText("Backward");
		m_buttBackward.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				m_buttBackwardActionPerformed(evt);
			}
		});

		jToolBar1.add(m_buttBackward);

		m_buttForward.setText(">");
		m_buttForward.setToolTipText("Forward");
		m_buttForward.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				m_buttForwardActionPerformed(evt);
			}
		});

		jToolBar1.add(m_buttForward);

		m_buttEnd.setText(">|");
		m_buttEnd.setToolTipText("End");
		m_buttEnd.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				m_buttEndActionPerformed(evt);
			}
		});

		jToolBar1.add(m_buttEnd);

		jToolBar1.setAlignmentX(LEFT_ALIGNMENT);
		toolBarPanel.add(jToolBar1, BorderLayout.WEST);

		m_moveLabel = new JLabel();
		m_moveLabel.setAlignmentX(CENTER_ALIGNMENT);
		JPanel moveLabelPanel = new JPanel();
		moveLabelPanel.add(m_moveLabel);
		toolBarPanel.add(moveLabelPanel, BorderLayout.CENTER);

		m_fenButton = new JButton("FEN");
		m_fenButton.addActionListener(new FenToClipBoard(() -> m_game.getPosition(), () -> m_parent));
		jToolBar2.add(m_fenButton);

		m_pgnButton = new JButton("PGN");
		m_pgnButton.addActionListener(new PgnToClipBoard(() -> m_game, () -> m_parent));

		jToolBar2.add(m_pgnButton);
		jToolBar2.setAlignmentX(RIGHT_ALIGNMENT);
		toolBarPanel.add(jToolBar2, BorderLayout.EAST);

		posPanel.add(toolBarPanel);

	}

	private void setHeaderLines() {
		String s = m_game.getHeaderString(0);
		if (!s.isEmpty()) {
			m_lbHeader0.setText(s);
		} else {
			m_lbHeader0.setText(" "); // an empty header collapses
		}
		s = m_game.getHeaderString(1);
		if (!s.isEmpty()) {
			m_lbHeader1.setText(s);
		} else {
			m_lbHeader1.setText(" "); // an empty header collapses
		}
	}

	private void m_buttEndActionPerformed(ActionEvent evt) {
		m_game.gotoEndOfLine();
	}

	private void m_buttForwardActionPerformed(ActionEvent evt) {
		m_game.goForward();
	}

	private void m_buttBackwardActionPerformed(ActionEvent evt) {
		m_game.goBack();
	}

	private void m_buttStartActionPerformed(ActionEvent evt) {
		m_game.gotoStart();
	}

	private void m_buttFlipActionPerformed(ActionEvent evt) {
		m_positionView.flip();
	}

	// Variables declaration - do not modify
	// private javax.swing.JButton m_buttBackToLineBegin;
	// private javax.swing.JButton m_buttEndOfLine;
	private javax.swing.JButton m_buttBackward;
	private javax.swing.JPanel m_textFrame;
	private javax.swing.JButton m_buttFlip;
	private javax.swing.JButton m_buttStart;
	private javax.swing.JSplitPane jSplitPane1;
	private javax.swing.JButton m_buttEnd;
	private javax.swing.JLabel m_lbHeader1;
	private javax.swing.JPanel toolBarPanel;
	private javax.swing.JToolBar jToolBar1;
	private javax.swing.JToolBar jToolBar2;
	private javax.swing.JPanel m_positionFrame;
	private javax.swing.JLabel m_lbHeader0;
	private javax.swing.JPanel jPanel1;
	private javax.swing.JButton m_buttForward;

	private javax.swing.JButton m_pgnButton;
	private javax.swing.JButton m_fenButton;
	private javax.swing.JPanel jPanel2;
	private javax.swing.JPanel jPanel3;

	// End of variables declaration

	// PositionListner

	@Override
	public void positionChanged(ChangeType type, ImmutablePosition pos, short move) {
		updateMovePane();
		highlightLastMove();
		if (m_positionView != null) {
			m_positionView.removeChessbaseDecorations();
		}
	}

	private void updateMovePane() {
		m_moveLabel.setText(m_game.getPosition().getLastMoveAsSanWithNumber());
	}

	private boolean m_userActionEnabled = true;

	public void setUserActionEnabled(final boolean userActionEnabled) {
		m_userActionEnabled = userActionEnabled;

		m_buttFlip.setEnabled(userActionEnabled);
		m_buttStart.setEnabled(userActionEnabled);
		m_buttBackward.setEnabled(userActionEnabled);
		m_buttForward.setEnabled(userActionEnabled);
		m_buttEnd.setEnabled(userActionEnabled);
		m_fenButton.setEnabled(userActionEnabled);
		m_pgnButton.setEnabled(userActionEnabled);

		Component[] components = jPanel3.getComponents();
		for (Component component : components) {
			component.setEnabled(userActionEnabled);
		}
	}

	protected void addToHeaderOnTheRight(final JComponent component) {
		jPanel3.removeAll();
		jPanel3.setAlignmentX(RIGHT_ALIGNMENT);
		jPanel3.add(component);
		jPanel3.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
	}

	private void addPopupToPositionView() {
		m_positionView.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (!m_userActionEnabled) {
					return;
				}
				if (SwingUtilities.isRightMouseButton(event)) {
					JPopupMenu popup = new JPopupMenu();
					JMenuItem deleteColorCommentsMenuItem = new JMenuItem("Delete color comments");
					deleteColorCommentsMenuItem.addActionListener(e -> {
						m_positionView.removeChessbaseDecorations();
						m_positionView.repaint();
					});
					popup.add(deleteColorCommentsMenuItem);
					popup.show(m_positionView, event.getX(), event.getY());
				}
			}
		});
	}

	private void addPopupToTextViewer() {
		m_textViewer.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				if (!m_userActionEnabled) {
					return;
				}
				// The behavior of DefaultCaret doesn't select the move, if it is a right
				// click, while a left click works. So, the caret has to be set here manually in
				// order to get the *usual* (expected) behavior. Moreover, this has to be done
				// in mousePressed (and not below in mouseClicked) because otherwise the caret
				// position is updated at a later point of time (a phenomenon which I didn't
				// understand at all).
				if (SwingUtilities.isRightMouseButton(event)) {
					int rightClickCaretPosition = m_textViewer.viewToModel2D(event.getPoint());
					m_textViewer.setCaretPosition(rightClickCaretPosition);
				}
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				if (!m_userActionEnabled) {
					return;
				}
				if (!SwingUtilities.isRightMouseButton(event)) {
					return;
				}

				TextViewerPopup popup = new TextViewerPopup(m_game, m_textViewer, GameBrowser.this);
				popup.show(m_textViewer, event.getX(), event.getY());
			}
		});
	}

	public int getDividerLocation() {
		return jSplitPane1.getDividerLocation();
	}

	public void setDividerLocation(int location) {
		jSplitPane1.setDividerLocation(location);
	}

	public int getDividerSize() {
		return jSplitPane1.getDividerSize();
	}

	@Override
	public void headerModelChanged(Game game) {
		setHeaderLines();
	}

	@Override
	public void moveModelChanged(Game game) {
	}
}
