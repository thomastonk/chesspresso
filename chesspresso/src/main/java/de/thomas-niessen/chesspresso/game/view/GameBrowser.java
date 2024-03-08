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
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import chesspresso.Chess;
import chesspresso.ScreenShot;
import chesspresso.game.Game;
import chesspresso.game.GameModelChangeListener;
import chesspresso.game.view.GameTextViewer.TextCreationType;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.position.FEN;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.OneClickMove;
import chesspresso.position.Position;
import chesspresso.position.PositionListener;
import chesspresso.position.PositionMotionListener;
import chesspresso.position.view.AllFensToClipBoard;
import chesspresso.position.view.Decoration.DecorationType;
import chesspresso.position.view.DecorationFactory;
import chesspresso.position.view.FenToClipBoard;
import chesspresso.position.view.PgnToClipBoard;
import chesspresso.position.view.PieceTracker;
import chesspresso.position.view.PositionView;
import chesspresso.position.view.PositionViewProperties;

/**
 * Game browser.
 *
 * @author Bernhard Seybold
 */
@SuppressWarnings("serial")
public class GameBrowser extends JPanel implements PositionMotionListener, PositionListener, GameModelChangeListener, ScreenShot {

	private Game game;
	private PositionView positionView;
	private GameTextViewer textViewer;
	protected UserAction userAction;
	private boolean oneClickMoves;

	private Component parent = null;
	private JLabel moveLabel = null;

	private boolean highlightLastMove;

	// ======================================================================

	/**
	 * Create a new game browser.
	 *
	 * @param game the game to be displayed
	 */
	public GameBrowser(Game game) {
		this(game, Chess.WHITE, UserAction.ENABLED, false);
	}

	/**
	 * Create a new game browser.
	 *
	 * @param game            the game to be displayed
	 * @param boardOnTheRight instead board on the left-hand side
	 */
	public GameBrowser(Game game, boolean boardOnTheRight) {
		this(game, Chess.WHITE, UserAction.ENABLED, boardOnTheRight);
	}

	/**
	 * Create a new game browser.
	 *
	 * @param game            the game to be displayed
	 * @param bottomPlayer    the player on the lower edge
	 * @param boardOnTheRight instead board on the left-hand side
	 */
	public GameBrowser(Game game, int bottomPlayer, boolean boardOnTheRight) {
		this(game, bottomPlayer, UserAction.ENABLED, boardOnTheRight);
	}

	/**
	 * Create a new game browser.
	 *
	 * @param game            the game to be displayed
	 * @param bottomPlayer    the player on the lower edge
	 * @param userAction      allowed user action
	 * @param boardOnTheRight instead board on the left-hand side
	 */
	public GameBrowser(Game game, int bottomPlayer, UserAction userAction, boolean boardOnTheRight) {
		super();
		initComponents(boardOnTheRight);
		setGame(game, bottomPlayer);

		positionView.setShowCoordinates(true);
		positionView.setDecorationsEnabled(true);
		positionView.setFocusable(false);
		positionFrame.add(positionView, BorderLayout.CENTER);

		textFrame.add(new JScrollPane(textViewer), BorderLayout.CENTER);

		setUserAction(userAction);
		oneClickMoves = false;
		addPopupToPositionView();

		positionView.setPieceTracker(new PieceTracker(game));

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent componentEvent) {
				if (boardOnTheRight) {
					setDividerLocation(getSize().width - getDividerSize() - 8 * positionView.getSquareSize());
				} else {
					setDividerLocation(Math.min(getSize().width, 8 * positionView.getSquareSize()));
				}
			}
		});

		// TN: guarantees a suitable maximum height
		setMaximumSize(new Dimension(2000, positionView.getPreferredSize().height + 100));
		// The value 100 is larger than the height of the two header lines plus the
		// height of the tools under the position view. Other values work, too, but too
		// small values, say 30 cause problems.
	}

	// ======================================================================

	public void setGame(Game game, int bottomPlayer) {
		if (game != null) {
			if (game != null) {
				game.getPosition().removePositionListener(this);
				game.removeChangeListener(this);
			}
			this.game = game;
			game.gotoStart();
			game.getPosition().addPositionListener(this);
			game.addChangeListener(this);

			if (positionView == null) {
				positionView = new PositionView(game.getPosition(), bottomPlayer, userAction);
				positionView.setShowCoordinates(true);
			} else {
				positionView.setPosition(game.getPosition());
				positionView.setBottomPlayer(bottomPlayer);
			}
			positionView.setPositionMotionListener(this);

			if (textViewer == null) {
				textViewer = new GameTextViewer(game, userAction, this);
			} else {
				textViewer.setGame(game);
			}

			setHeaderLines();
			highlightLastMove();
			textViewer.showCurrentGameNode();
		}
	}

	// ======================================================================

	public void setProperties(PositionViewProperties props) {
		positionView.setProperties(props);
		invalidate();
	}

	// ======================================================================

	public void setProperties(final Font f, final Color whiteSquares, final Color blackSquares) {
		if (positionView != null) {
			positionView.setFont(f);
			positionView.setWhiteSquareColor(whiteSquares);
			positionView.setBlackSquareColor(blackSquares);
			invalidate();
		}
	}

	@Override
	public void setFont(final Font f) {
		if (positionView != null) {
			positionView.setFont(f);
			invalidate();
		}
	}

	@Override
	public Font getFont() {
		if (positionView != null) {
			return positionView.getFont();
		} else {
			return null;
		}
	}

	public void setWhiteSquareColor(final Color whiteSquares) {
		if (positionView != null) {
			positionView.setWhiteSquareColor(whiteSquares);
			invalidate();
		}
	}

	public void setBlackSquareColor(final Color blackSquares) {
		if (positionView != null) {
			positionView.setBlackSquareColor(blackSquares);
			invalidate();
		}
	}

	public void setHighlightLastMove(boolean hlm) {
		highlightLastMove = hlm;
		highlightLastMove();
	}

	public boolean isHighlightingLastMove() {
		return highlightLastMove;
	}

	public Game getGame() {
		return game;
	}

	public String getCurrentPositionAsFEN() {
		return FEN.getFEN(game.getPosition());
	}

	public Font getPositionViewFont() {
		if (positionView != null) {
			return positionView.getFont();
		} else {
			return null;
		}
	}

	public Color getWhiteSquareColor() {
		if (positionView != null) {
			return positionView.getWhiteSquareColor();
		} else {
			return null;
		}
	}

	public Color getBlackSquareColor() {
		if (positionView != null) {
			return positionView.getBlackSquareColor();
		} else {
			return null;
		}
	}

	public PositionView getPositionView() {
		return positionView;
	}

	public JPanel getToolbar() {
		return toolBarPanel;
	}

	public void highlightFirstHeaderLine(boolean highlight) {
		lbHeader0.setOpaque(highlight);
	}

	public void highlightSecondHeaderLine(boolean highlight) {
		lbHeader1.setOpaque(highlight);
	}

	public void allowOneClickMoves(boolean allow) {
		oneClickMoves = allow;
	}

	// ======================================================================
	// Methods to implement PositionMotionListener

	@Override
	public boolean isDragAllowed(ImmutablePosition position, int from) {
		// allow dragging only if editable and there is a stone on the square
		return userAction == UserAction.ENABLED && game.getPosition().getStone(from) != Chess.NO_STONE;
	}

	@Override
	public void dragged(ImmutablePosition position, int from, int to, MouseEvent e) {
		if (userAction != UserAction.ENABLED) {
			return;
		}
		try {
			Position pos = game.getPosition();
			int row = Chess.sqiToRow(to);
			if (pos.getPiece(from) == Chess.PAWN && (row == 0 || row == 7)) {
				pos.doMove(pos.getMove(from, to, Chess.QUEEN));
			} else {
				pos.doMove(pos.getMove(from, to, Chess.NO_PIECE));
			}
			// TN: This code is not complete, because underpromotions are not possible.
		} catch (IllegalMoveException ignore) {
		}
	}

	@Override
	public void squareClicked(ImmutablePosition position, int sqi, MouseEvent e) {
		if (userAction == UserAction.ENABLED && oneClickMoves && game.getPosition() == position) {
			OneClickMove.squareClicked(game.getPosition(), sqi, e);
		}
	}

	// ======================================================================

	public int getBottomPlayer() {
		return positionView.getBottomPlayer();
	}

	public void setBottomPlayer(int player) {
		positionView.setBottomPlayer(player);
	}

	public void setParent(Component c) {
		parent = c;
	}

	// =======================================================================

	public void highlightLastMove() {
		if (positionView == null) {
			return;
		}
		positionView.removeDecorations(DecorationType.ARROW, Color.BLUE, GameBrowser.this);
		if (highlightLastMove) {
			Move lastMove = game.getLastMove();
			if (lastMove != null && lastMove.getShortMoveDesc() != Move.NULL_MOVE) {
				if (!lastMove.isCastle() && !lastMove.isCastleChess960()) {
					positionView.addDecoration(DecorationFactory.getArrowDecoration(lastMove.getFromSqi(), lastMove.getToSqi(),
							Color.BLUE, GameBrowser.this), false);
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
					positionView.addDecoration(
							DecorationFactory.getArrowDecoration(fromSquare, toSquare, Color.BLUE, GameBrowser.this), false);
					positionView.addDecoration(
							DecorationFactory.getArrowDecoration(toSquare, fromSquare, Color.BLUE, GameBrowser.this), false);
				}
			}
		}
		positionView.repaint();
	}

	// =======================================================================

	public void flip() {
		positionView.flip();
	}

	// =======================================================================

	public void setupPuzzleMode() {
		if (getTextCreationType() == TextCreationType.PUZZLE_MODE) {
			if (game.getTag(PGN.TAG_FEN) != null) { // only fragments are treated as puzzles	
				game.gotoStart();
				game.goForward();
				setBottomPlayer(game.getPosition().getToPlay());
			}
		}
	}

	// =======================================================================
	private void initComponents(boolean boardOnTheRight) {
		jPanel1 = new javax.swing.JPanel();
		jPanel2 = new javax.swing.JPanel();
		jPanel3 = new javax.swing.JPanel();
		lbHeader0 = new javax.swing.JLabel();
		lbHeader0.setBackground(Color.ORANGE);
		lbHeader1 = new javax.swing.JLabel();
		lbHeader1.setBackground(Color.ORANGE);
		jSplitPane1 = new javax.swing.JSplitPane();
		positionFrame = new javax.swing.JPanel();
		textFrame = new javax.swing.JPanel();
		toolBarPanel = new javax.swing.JPanel();
		jToolBar1 = new javax.swing.JToolBar();
		jToolBar1.setFloatable(false);
		jToolBar2 = new javax.swing.JToolBar();
		jToolBar2.setFloatable(false);
		buttFlipBoard = new javax.swing.JButton();
		buttStart = new javax.swing.JButton();
		buttBackward = new javax.swing.JButton();
		buttForward = new javax.swing.JButton();
		buttEnd = new javax.swing.JButton();

		setLayout(new java.awt.BorderLayout());

		toolBarPanel.setLayout(new java.awt.BorderLayout());

		jPanel1.setLayout(new BorderLayout());
		jPanel2.setLayout(new javax.swing.BoxLayout(jPanel2, javax.swing.BoxLayout.Y_AXIS));
		jPanel3.setLayout(new javax.swing.BoxLayout(jPanel3, javax.swing.BoxLayout.Y_AXIS));
		jPanel1.add(jPanel2, BorderLayout.CENTER);
		jPanel1.add(jPanel3, BorderLayout.EAST);
		jPanel1.setFocusable(false);

		lbHeader0.setText("0");
		jPanel2.add(lbHeader0);

		lbHeader1.setText("1");
		jPanel2.add(lbHeader1);

		lbHeader0.setAlignmentX(CENTER_ALIGNMENT);
		lbHeader1.setAlignmentX(CENTER_ALIGNMENT);

		add(jPanel1, java.awt.BorderLayout.NORTH);

		positionFrame.setLayout(new javax.swing.BoxLayout(positionFrame, javax.swing.BoxLayout.X_AXIS));

		JPanel posPanel = new JPanel();
		posPanel.setLayout(new javax.swing.BoxLayout(posPanel, javax.swing.BoxLayout.Y_AXIS));
		posPanel.add(positionFrame);
		if (!boardOnTheRight) {
			jSplitPane1.setLeftComponent(posPanel);
		} else {
			jSplitPane1.setRightComponent(posPanel);
		}

		textFrame.setLayout(new java.awt.BorderLayout());

		textFrame.setMinimumSize(new java.awt.Dimension(256, 128));
		textFrame.setPreferredSize(new java.awt.Dimension(256, 256));
		// textFrame.setPreferredSize(new java.awt.Dimension(330, 256));
		// TN: Generally the PositionView should take its required size and not more.
		// And the GameTextViewer should have the remaining space in the component.
		// But this does not happen, if the GameTextViewer is on the left. Here 330 is
		// somehow a magic number. The handling should be improved.
		if (!boardOnTheRight) {
			jSplitPane1.setRightComponent(textFrame);
		} else {
			jSplitPane1.setLeftComponent(textFrame);
		}

		add(jSplitPane1, java.awt.BorderLayout.CENTER);

		buttFlipBoard.setText("^");
		buttFlipBoard.setToolTipText("Flip board");
		buttFlipBoard.addActionListener(this::buttFlipBoardActionPerformed);

		jToolBar1.add(buttFlipBoard);

		buttStart.setText("|<");
		buttStart.setToolTipText("Start");
		buttStart.addActionListener(this::buttStartActionPerformed);

		jToolBar1.add(buttStart);

		buttBackward.setText("<");
		buttBackward.setToolTipText("Backward");
		buttBackward.addActionListener(this::buttBackwardActionPerformed);

		jToolBar1.add(buttBackward);

		buttForward.setText(">");
		buttForward.setToolTipText("Forward");
		buttForward.addActionListener(this::buttForwardActionPerformed);

		jToolBar1.add(buttForward);

		buttEnd.setText(">|");
		buttEnd.setToolTipText("End");
		buttEnd.addActionListener(this::buttEndActionPerformed);

		jToolBar1.add(buttEnd);

		jToolBar1.setAlignmentX(LEFT_ALIGNMENT);
		toolBarPanel.add(jToolBar1, BorderLayout.WEST);

		moveLabel = new JLabel();
		moveLabel.setAlignmentX(CENTER_ALIGNMENT);
		JPanel moveLabelPanel = new JPanel();
		moveLabelPanel.add(moveLabel);
		toolBarPanel.add(moveLabelPanel, BorderLayout.CENTER);

		fenButton = new JButton("FEN");
		fenButton.addActionListener(new FenToClipBoard(() -> game.getPosition(), () -> parent));
		jToolBar2.add(fenButton);

		allFensButton = new JButton("FENs");
		allFensButton.addActionListener(new AllFensToClipBoard(() -> game, () -> parent));
		allFensButton.setToolTipText("Copy all mainline FENs to system clipboard");
		jToolBar2.add(allFensButton);

		pgnButton = new JButton("PGN");
		pgnButton.addActionListener(new PgnToClipBoard(() -> game, () -> parent));

		jToolBar2.add(pgnButton);
		jToolBar2.setAlignmentX(RIGHT_ALIGNMENT);
		toolBarPanel.add(jToolBar2, BorderLayout.EAST);

		posPanel.add(toolBarPanel);

	}

	private void setHeaderLines() {
		String s = game.getHeaderString(0);
		if (!s.isEmpty()) {
			lbHeader0.setText(s);
		} else {
			lbHeader0.setText(" "); // an empty header collapses
		}
		s = game.getHeaderString(1);
		if (!s.isEmpty()) {
			lbHeader1.setText(s);
		} else {
			lbHeader1.setText(" "); // an empty header collapses
		}
	}

	private void buttEndActionPerformed(ActionEvent evt) {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.gotoEndOfLine();
		}
	}

	private void buttForwardActionPerformed(ActionEvent evt) {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.goForward();
		}
	}

	private void buttBackwardActionPerformed(ActionEvent evt) {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.goBack();
		}
	}

	private void buttStartActionPerformed(ActionEvent evt) {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.gotoStart();
		}
	}

	private void buttFlipBoardActionPerformed(ActionEvent evt) {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			positionView.flip();
		}
	}

	private javax.swing.JButton buttBackward;
	private javax.swing.JPanel textFrame;
	private javax.swing.JButton buttFlipBoard;
	private javax.swing.JButton buttStart;
	private javax.swing.JSplitPane jSplitPane1;
	private javax.swing.JButton buttEnd;
	private javax.swing.JLabel lbHeader1;
	private javax.swing.JPanel toolBarPanel;
	private javax.swing.JToolBar jToolBar1;
	private javax.swing.JToolBar jToolBar2;
	private javax.swing.JPanel positionFrame;
	private javax.swing.JLabel lbHeader0;
	private javax.swing.JPanel jPanel1;
	private javax.swing.JButton buttForward;

	private javax.swing.JButton fenButton;
	private javax.swing.JButton allFensButton;
	private javax.swing.JButton pgnButton;
	private javax.swing.JPanel jPanel2;
	private javax.swing.JPanel jPanel3;

	// End of variables declaration

	// PositionListner

	@Override
	public void positionChanged(ChangeType type, short move, String fen) {
		updateMovePane();
		highlightLastMove();
		if (positionView != null) {
			positionView.removeChessbaseDecorations();
		}
	}

	private void updateMovePane() {
		moveLabel.setText(game.getPosition().getLastMoveAsSanWithNumber());
	}

	protected void addToHeaderOnTheRight(final JComponent component) {
		jPanel3.removeAll();
		jPanel3.setAlignmentX(RIGHT_ALIGNMENT);
		jPanel3.add(component);
		jPanel3.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
	}

	private void removeColorComments() {
		positionView.removeChessbaseDecorations();
		positionView.removeAllPieceTracking(true);
	}

	private void removeAllNumbers() {
		positionView.removeDecorations(DecorationType.NUMBER_IN_SQUARE, Color.DARK_GRAY, GameBrowser.this);
	}

	private void addPopupToPositionView() {
		positionView.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (SwingUtilities.isRightMouseButton(event)) {
					JPopupMenu popup = new JPopupMenu();
					{
						JMenuItem trackPieceItem = new JMenuItem("Track the piece");
						trackPieceItem.addActionListener(
								e -> positionView.addToPieceTracking(positionView.getSquare(event.getX(), event.getY())));
						popup.add(trackPieceItem);
					}
					{
						JMenuItem trackPieceItem = new JMenuItem("Untrack the piece");
						trackPieceItem.addActionListener(
								e -> positionView.removeFromPieceTracking(positionView.getSquare(event.getX(), event.getY())));
						popup.add(trackPieceItem);
					}
					popup.add(new JSeparator());

					JMenuItem deleteColorCommentsMenuItem = new JMenuItem("Remove color comments");
					deleteColorCommentsMenuItem.addActionListener(e -> removeColorComments());
					popup.add(deleteColorCommentsMenuItem);
					popup.add(new JSeparator());

					for (int i = 0; i <= 9; ++i) {
						int iFinal = i;
						JMenuItem addNumberMenuItem = new JMenuItem("Set number " + iFinal);
						addNumberMenuItem.addActionListener(e -> {
							int square = positionView.getSquare(event.getX(), event.getY());
							positionView.removeDecorations(DecorationType.NUMBER_IN_SQUARE, Color.DARK_GRAY, GameBrowser.this,
									d -> d.getType() == DecorationType.NUMBER_IN_SQUARE && d.getSquare() == square);
							positionView.addDecoration(
									DecorationFactory.getNumberInSquare(square, Color.DARK_GRAY, GameBrowser.this, iFinal), true);
						});
						popup.add(addNumberMenuItem);
					}
					popup.add(new JSeparator());

					JMenuItem removeNumberMenuItem = new JMenuItem("Remove number from square");
					removeNumberMenuItem.addActionListener(e -> positionView.removeDecorations(DecorationType.NUMBER_IN_SQUARE,
							Color.DARK_GRAY, GameBrowser.this, d -> d.getType() == DecorationType.NUMBER_IN_SQUARE
									&& d.getSquare() == positionView.getSquare(event.getX(), event.getY())));
					popup.add(removeNumberMenuItem);

					JMenuItem removeAllNumbersMenuItem = new JMenuItem("Remove all numbers from all squares");
					removeAllNumbersMenuItem.addActionListener(e -> removeAllNumbers());
					popup.add(removeAllNumbersMenuItem);

					popup.show(positionView, event.getX(), event.getY());
				}
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

	public TextCreationType getTextCreationType() {
		if (textViewer != null) {
			return textViewer.getTextCreationType();
		} else {
			return null;
		}
	}

	public void setTextCreationType(TextCreationType type) {
		if (textViewer != null) {
			textViewer.setTextCreationType(type);
		}
	}

	public void setUserAction(UserAction userAction) {
		this.userAction = userAction;
		positionView.setUserAction(userAction);
		textViewer.setUserAction(userAction);

		updateComponents();
	}

	public void removePieceTracking() {
		positionView.removeAllPieceTracking(true);
	}

	private void updateComponents() {
		boolean navButtons = userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE;
		buttFlipBoard.setEnabled(navButtons);
		buttStart.setEnabled(navButtons);
		buttBackward.setEnabled(navButtons);
		buttForward.setEnabled(navButtons);
		buttEnd.setEnabled(navButtons);
	}

	@Override
	public boolean doBoardScreenShot(String fileName) {
		return ScreenShot.saveScreenShot(positionView, fileName);
	}
}
