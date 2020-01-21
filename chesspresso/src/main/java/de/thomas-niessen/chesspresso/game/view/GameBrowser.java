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
package chesspresso.game.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.game.GameModel;
import chesspresso.game.GameModelChangeListener;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.pgn.PGNWriter;
import chesspresso.position.FEN;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.NAG;
import chesspresso.position.PositionChangeListener;
import chesspresso.position.PositionMotionListener;
import chesspresso.position.view.Decoration.DecorationType;
import chesspresso.position.view.DecorationFactory;
import chesspresso.position.view.FenToClipBoard;
import chesspresso.position.view.PositionView;
import chesspresso.position.view.PositionViewProperties;

/**
 * Game browser.
 *
 * @author Bernhard Seybold
 */
@SuppressWarnings("serial")
public class GameBrowser extends JPanel
	implements PositionMotionListener, PositionChangeListener, GameModelChangeListener {

    private Game m_game;
    private PositionView m_positionView;
    private GameTextViewer m_textViewer;
    private boolean m_editable;
    private boolean m_extraButtons;

    private Component m_parent = null;
    private JLabel m_moveLabel = null;

    private boolean m_highlightLastMove;

    private InputDialog inputDialog = new DefaultInputDialog();

    // ======================================================================

    /**
     * Create a new game browser.
     *
     * @param gameModel the game model to be displayed
     */
    public GameBrowser(GameModel gameModel) {
	this(new Game(gameModel), Chess.WHITE, false);
    }

    /**
     * Create a new game browser.
     *
     * @param game the game to be displayed
     */
    public GameBrowser(Game game) {
	this(game, Chess.WHITE, false, false, false);
    }

    /**
     * Create a new game browser.
     *
     * @param game            the game to be displayed
     * @param boardOnTheRight instead board on the left-hand side
     */
    public GameBrowser(Game game, boolean boardOnTheRight) {
	this(game, Chess.WHITE, false, boardOnTheRight, false);
    }

    /**
     * Create a new game browser.
     *
     * @param game            the game to be displayed
     * @param bottomPlayer    the player on the lower edge
     * @param boardOnTheRight instead board on the left-hand side
     */
    public GameBrowser(Game game, int bottomPlayer, boolean boardOnTheRight) {
	this(game, bottomPlayer, false, boardOnTheRight, false);
    }

    /**
     * Create a new game browser.
     *
     * @param game            the game to be displayed
     * @param bottomPlayer    the player on the lower edge
     * @param editable        whether the game can be edited by the view
     * @param boardOnTheRight instead board on the left-hand side
     * @param extraButtons    show NAG, comment and delete button
     */
    public GameBrowser(Game game, int bottomPlayer, boolean editable, boolean boardOnTheRight, boolean extraButtons) {
	super();
	initComponents(boardOnTheRight);
	setGame(game, bottomPlayer);

	m_positionView.setShowSqiEP(false);
	m_positionView.setFocusable(false);
	m_positionFrame.add(m_positionView, BorderLayout.CENTER);

	m_textFrame.add(new JScrollPane(m_textViewer), BorderLayout.CENTER);

	m_editable = editable;
	m_extraButtons = extraButtons;
	if (!m_extraButtons) {
	    m_buttNAG.setVisible(false);
	    m_buttPreComment.setVisible(false);
	    m_buttPostComment.setVisible(false);
	    m_buttDelete.setVisible(false);
	    addPopupToPositionView();
	    addPopupToTextViewer();
	}

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
	// small values, say 30 cause problems. TODO Observe.
    }

    // ======================================================================

    public void setGame(Game game, int bottomPlayer) {
	if (game != null) {
	    if (m_game != null) {
		m_game.getPosition().removePositionChangeListener(this);
		m_game.removeChangeListener(this);
	    }
	    m_game = game;
	    m_game.gotoStart();
	    m_game.getPosition().addPositionChangeListener(this);
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

    public void setInputDialog(InputDialog inputDialog) {
	if (inputDialog != null) {
	    this.inputDialog = inputDialog;
	}
    }

    // ======================================================================
    // Methods to implement PositionMotionListener

    @Override
    public boolean allowDrag(ImmutablePosition position, int from) {
	// allow dragging only if editable and there is a stone on the square
	return m_editable && m_game.getPosition().getStone(from) != Chess.NO_STONE;
    }

    @Override
    public int getPartnerSqi(ImmutablePosition position, int from) {
	return Chess.NO_SQUARE; // =====>
    }

    @Override
    public void dragged(ImmutablePosition position, int from, int to, MouseEvent e) {
	try {
	    m_game.getPosition().doMove(m_game.getPosition().getMove(from, to, Chess.NO_PIECE));
	    // TN: this code is not correct, because no promotion is possible.
	} catch (IllegalMoveException ex) {
	    ex.printStackTrace();
	}
    }

    @Override
    public void squareClicked(ImmutablePosition position, int sqi, MouseEvent e) {
	// nothing
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
		m_positionView.addDecoration(
			DecorationFactory.getArrowDecoration(lastMove.getFromSqi(), lastMove.getToSqi(), Color.BLUE),
			false);
	    }
	}
	m_positionView.repaint();
    }

    // =======================================================================

    public void flip() {
	m_positionView.flip();
    }

    // =======================================================================
    private void initComponents(boolean boardOnTheRight) {// GEN-BEGIN:initComponents
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
	m_buttNAG = new javax.swing.JButton();
	m_buttPreComment = new javax.swing.JButton();
	m_buttPostComment = new javax.swing.JButton();
	m_buttDelete = new javax.swing.JButton();

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
	// somehow a magic number. The handling should be improved. TODO
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
	m_buttForward.setToolTipText("Foward");
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

	m_buttNAG.setText("NAG");
	m_buttNAG.setToolTipText("NAG");
	m_buttNAG.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent evt) {
		m_buttNAGActionPerformed(evt);
	    }
	});

	jToolBar1.add(m_buttNAG);

	m_buttPreComment.setText("comment before move");
	m_buttPreComment.setToolTipText("Comment before move");
	m_buttPreComment.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent evt) {
		m_buttPreCommentActionPerformed(evt);
	    }
	});

	jToolBar1.add(m_buttPreComment);

	m_buttPostComment.setText("comment after move");
	m_buttPostComment.setToolTipText("Comment after move");
	m_buttPostComment.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent evt) {
		m_buttPostCommentActionPerformed(evt);
	    }
	});

	jToolBar1.add(m_buttPostComment);

	m_buttDelete.setText("del");
	m_buttDelete.setToolTipText("Delete line");
	m_buttDelete.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent evt) {
		m_buttDeleteActionPerformed(evt);
	    }
	});

	jToolBar1.add(m_buttDelete);
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
	m_pgnButton.addActionListener(new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent evt) {
		String s;
		try {
		    s = PGNWriter.writeToString(m_game);
		} catch (IllegalArgumentException e) {
		    JOptionPane.showMessageDialog(GameBrowser.this,
			    "Unable to generate PGN:" + System.lineSeparator() + e.getMessage(), "Error",
			    JOptionPane.ERROR_MESSAGE);
		    return;
		}
		JDialog pgnDialog = new JDialog();
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
		copyButton.addActionListener(e -> {
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
		if (m_parent != null) {
		    pgnDialog.setLocationRelativeTo(m_parent);
		}
		if (pgnDialog.getSize().height > 600) {
		    pgnDialog.setSize(new Dimension(pgnDialog.getSize().width, 600));
		}
		pgnDialog.setVisible(true);
	    }
	});

	jToolBar2.add(m_pgnButton);
	jToolBar2.setAlignmentX(RIGHT_ALIGNMENT);
	toolBarPanel.add(jToolBar2, BorderLayout.EAST);

	posPanel.add(toolBarPanel);

    }// GEN-END:initComponents

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
	// TN added:
	m_game.getPosition().firePositionChanged();
    }

    private void m_buttDeleteActionPerformed(ActionEvent evt) {
	m_game.deleteCurrentLine();
	m_textViewer.moveModelChanged(null);
    }

    private void m_buttPostCommentActionPerformed(ActionEvent evt) {
	String currentComment = m_game.getPostMoveComment();
	String move = m_game.getLastMoveAsSanWithNumber();
	String message;
	if (move == null || move.isEmpty()) {
	    message = "Your comment:";
	} else {
	    message = "Your comment after " + move + ":";
	}
	Object comment = inputDialog.showInputDialog(this, "Edit a comment", message, currentComment);
	if (comment != null) {
	    String newComment = comment.toString().replaceAll(System.lineSeparator(), " ").replaceAll("\\n", " ")
		    .replaceAll("\\{", "(").replaceAll("\\}", ")");
	    if (!newComment.equals(currentComment)) {
		if (m_game.getNumOfPlies() > 0) {
		    m_game.setPostMoveComment(newComment);
		} else {
		    m_game.setEmptyGameComment(newComment);
		}
		m_textViewer.moveModelChanged(null);
	    }
	}
    }

    private void m_buttPreCommentActionPerformed(ActionEvent evt) {
	String currentComment = m_game.getPreMoveComment();
	String move = m_game.getLastMoveAsSanWithNumber();
	String message;
	if (move == null || move.isEmpty()) {
	    message = "Your comment:";
	} else {
	    message = "Your comment before " + move + ":";
	}
	Object comment = inputDialog.showInputDialog(this, "Edit a comment", message, currentComment);
	if (comment != null) {
	    String newComment = comment.toString().replaceAll(System.lineSeparator(), " ").replaceAll("\\n", " ")
		    .replaceAll("\\{", "(").replaceAll("\\}", ")");
	    if (!newComment.equals(currentComment)) {
		if (m_game.getNumOfPlies() > 0) {
		    m_game.setPreMoveComment(newComment);
		} else {
		    m_game.setEmptyGameComment(newComment);
		}
		m_textViewer.moveModelChanged(null);
	    }
	}
    }

    private void m_buttNAGActionPerformed(ActionEvent evt) {
	javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();

	ActionListener actionListener = new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent event) {
		short nag = NAG.ofString(event.getActionCommand());
		int curNode = m_game.getCurNode();
		if (curNode > 0) {
		    if (m_game.currentMoveHasNag(nag)) {
			m_game.removeNag(nag);
		    } else {
			m_game.addNag(nag);
		    }
		    m_game.gotoNode(curNode);
		}
	    }
	};

	String[] nags = NAG.getDefinedShortNags();
	for (int i = 0; i < nags.length; i++) {
	    boolean isChecked = m_game.currentMoveHasNag(NAG.ofString(nags[i]));
	    JMenuItem item = new JCheckBoxMenuItem(nags[i], isChecked);
	    popup.add(item);
	    item.addActionListener(actionListener);
	    item.setActionCommand(nags[i]);
	}

	popup.show(m_buttNAG, 0, 0);
    }

    private void m_buttForwardActionPerformed(ActionEvent evt) {
	m_game.goForward();
	m_game.getPosition().firePositionChanged();
    }

    private void m_buttBackwardActionPerformed(ActionEvent evt) {
	m_game.goBack();
	m_game.getPosition().firePositionChanged();
    }

    private void m_buttStartActionPerformed(ActionEvent evt) {
	m_game.gotoStart();
	m_game.getPosition().firePositionChanged();
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
    private javax.swing.JButton m_buttNAG;
    private javax.swing.JLabel m_lbHeader0;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton m_buttDelete;
    private javax.swing.JButton m_buttPreComment;
    private javax.swing.JButton m_buttPostComment;
    private javax.swing.JButton m_buttForward;

    private javax.swing.JButton m_pgnButton;
    private javax.swing.JButton m_fenButton;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;

    // End of variables declaration//GEN-END:variables

    /*
     * (non-Javadoc)
     * 
     * @see chesspresso.position.PositionChangeListener#notifyPositionChanged(
     * chesspresso.position.ImmutablePosition)
     */
    @Override
    public void notifyPositionChanged(ImmutablePosition position) {
	// it shall not be allowed to replace the position!
	updateMovePane();
	highlightLastMove();
	if (m_positionView != null) {
	    m_positionView.removeChessbaseDecorations();
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see chesspresso.position.PositionChangeListener#notifyMoveDone(chesspresso.
     * position.ImmutablePosition, short)
     */
    @Override
    public void notifyMoveDone(ImmutablePosition position, short move) {
	updateMovePane();
	highlightLastMove();
	if (m_positionView != null) {
	    m_positionView.removeChessbaseDecorations();
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * chesspresso.position.PositionChangeListener#notifyMoveUndone(chesspresso.
     * position.ImmutablePosition)
     */
    @Override
    public void notifyMoveUndone(ImmutablePosition position) {
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

		JPopupMenu popup = new JPopupMenu();

		ActionListener actionListener = new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent e) {
			short nag = NAG.ofString(e.getActionCommand());
			int curNode = m_game.getCurNode();
			if (curNode > 0) {
			    if (NAG.isEvaluation(nag)) {
				m_game.removeEvaluationNags();
			    }
			    if (NAG.isPunctuationMark(nag)) {
				m_game.removePunctuationNags();
			    }
			    m_game.addNag(nag);
			    m_game.gotoNode(curNode);
			}
		    }
		};
		ActionListener removeEvaluation = new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent e) {
			int curNode = m_game.getCurNode();
			if (curNode > 0) {
			    m_game.removeEvaluationNags();
			}
			m_game.gotoNode(curNode);
		    }
		};
		ActionListener removePunctuation = new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent e) {
			int curNode = m_game.getCurNode();
			if (curNode > 0) {
			    m_game.removePunctuationNags();
			}
			m_game.gotoNode(curNode);
		    }
		};

		{
		    JMenu punctuationNagMenu = new JMenu("!, ?, ...");
		    String[] nags2 = NAG.getDefinedShortNags(NAG.PUNCTUATION_NAG_BEGIN, NAG.PUNCTUATION_NAG_END);
		    for (int i = 0; i < nags2.length; i++) {
			JMenuItem item = new JMenuItem(nags2[i]);
			punctuationNagMenu.add(item);
			item.addActionListener(actionListener);
			item.setActionCommand(nags2[i]);
		    }
		    JMenuItem noneItem = new JMenuItem("none");
		    punctuationNagMenu.add(noneItem);
		    noneItem.addActionListener(removePunctuation);
		    popup.add(punctuationNagMenu);
		}
		{
		    JMenu evaluationNagMenu = new JMenu("+-, =, ...");
		    String[] nags1 = NAG.getDefinedShortNags(NAG.EVALUATION_NAG_BEGIN, NAG.EVALUATION_NAG_END);
		    for (int i = 0; i < nags1.length; i++) {
			JMenuItem item = new JMenuItem(nags1[i]);
			evaluationNagMenu.add(item);
			item.addActionListener(actionListener);
			item.setActionCommand(nags1[i]);
		    }
		    JMenuItem noneItem = new JMenuItem("none");
		    evaluationNagMenu.add(noneItem);
		    noneItem.addActionListener(removeEvaluation);
		    popup.add(evaluationNagMenu);
		}
		{
		    JMenuItem commentAfterMenuItem = new JMenuItem("Comment before move");
		    commentAfterMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_buttPreCommentActionPerformed(e);
			}
		    });
		    popup.add(commentAfterMenuItem);
		}
		{
		    JMenuItem commentAfterMenuItem = new JMenuItem("Comment after move");
		    commentAfterMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_buttPostCommentActionPerformed(e);
			}
		    });
		    commentAfterMenuItem.setEnabled(m_game.getNumOfPlies() > 0);
		    popup.add(commentAfterMenuItem);
		}
		popup.addSeparator();
		// result
		{
		    JMenu setResultMenu = new JMenu("Set result");
		    JMenuItem whiteWinsItem = new JMenuItem("1-0");
		    JMenuItem blackWinsItem = new JMenuItem("0-1");
		    JMenuItem drawItem = new JMenuItem("1/2-1/2");
		    JMenuItem unknownItem = new JMenuItem("*");
		    setResultMenu.add(whiteWinsItem);
		    setResultMenu.add(blackWinsItem);
		    setResultMenu.add(drawItem);
		    setResultMenu.add(unknownItem);
		    whiteWinsItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.setTag(PGN.TAG_RESULT, PGN.RESULT_WHITE_WINS);
			    setHeaderLines();
			    m_textViewer.moveModelChanged(m_game);
			}
		    });
		    blackWinsItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.setTag(PGN.TAG_RESULT, PGN.RESULT_BLACK_WINS);
			    setHeaderLines();
			    m_textViewer.moveModelChanged(m_game);
			}
		    });
		    drawItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.setTag(PGN.TAG_RESULT, PGN.RESULT_DRAW);
			    setHeaderLines();
			    m_textViewer.moveModelChanged(m_game);
			}
		    });
		    unknownItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.setTag(PGN.TAG_RESULT, PGN.RESULT_UNFINISHED);
			    setHeaderLines();
			    m_textViewer.moveModelChanged(m_game);
			}
		    });
		    popup.add(setResultMenu);
		}
		// PGN tags
		JMenu pgnTagsMenu = new JMenu("PGN tags");
		{
		    JMenuItem setEventMenuItem = new JMenuItem("Set event");
		    setEventMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object evnt = JOptionPane.showInputDialog(GameBrowser.this, "Event", "Set event",
				    JOptionPane.OK_CANCEL_OPTION, null, null, m_game.getEvent());
			    if (evnt != null) {
				m_game.setTag(PGN.TAG_EVENT, evnt.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setEventMenuItem);
		}
		{
		    JMenuItem setSiteMenuItem = new JMenuItem("Set site");
		    setSiteMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object site = JOptionPane.showInputDialog(GameBrowser.this, "Site", "Set site",
				    JOptionPane.OK_CANCEL_OPTION, null, null, m_game.getSite());
			    if (site != null) {
				m_game.setTag(PGN.TAG_SITE, site.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setSiteMenuItem);
		}
		{
		    JMenuItem setDateMenuItem = new JMenuItem("Set date");
		    setDateMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object date = JOptionPane.showInputDialog(GameBrowser.this,
				    "Date (yyyy.mm.dd, with ? for missing information)", "Set date",
				    JOptionPane.OK_CANCEL_OPTION, null, null, m_game.getDate());
			    if (date != null) {
				m_game.setTag(PGN.TAG_DATE, date.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setDateMenuItem);
		}
		{
		    JMenuItem setRoundMenuItem = new JMenuItem("Set round");
		    setRoundMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object round = JOptionPane.showInputDialog(GameBrowser.this,
				    "Round (please use PGN format)", "Set round", JOptionPane.OK_CANCEL_OPTION, null,
				    null, m_game.getRound());
			    if (round != null) {
				m_game.setTag(PGN.TAG_ROUND, round.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setRoundMenuItem);
		}
		{
		    JMenuItem setWhiteMenuItem = new JMenuItem("Set white player");
		    setWhiteMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object white = JOptionPane.showInputDialog(GameBrowser.this,
				    "White (family name, forenames (or initials)", "Set white player",
				    JOptionPane.OK_CANCEL_OPTION, null, null, m_game.getWhite());
			    if (white != null) {
				m_game.setTag(PGN.TAG_WHITE, white.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setWhiteMenuItem);
		}
		{
		    JMenuItem setBlackMenuItem = new JMenuItem("Set black player");
		    setBlackMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object black = JOptionPane.showInputDialog(GameBrowser.this,
				    "Black (family name, forenames (or initials)", "Set black player",
				    JOptionPane.OK_CANCEL_OPTION, null, null, m_game.getBlack());
			    if (black != null) {
				m_game.setTag(PGN.TAG_BLACK, black.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setBlackMenuItem);
		}
		{
		    JMenuItem setEcoMenuItem = new JMenuItem("Set ECO code");
		    setEcoMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object eco = JOptionPane.showInputDialog(GameBrowser.this, "ECO", "Set ECO code",
				    JOptionPane.OK_CANCEL_OPTION, null, null, m_game.getECO());
			    if (eco != null) {
				m_game.setTag(PGN.TAG_ECO, eco.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setEcoMenuItem);
		}
		{
		    JMenuItem setFenMenuItem = new JMenuItem("Set FEN");
		    setFenMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object fen = JOptionPane.showInputDialog(GameBrowser.this, "FEN",
				    "Set startposition by FEN", JOptionPane.OK_CANCEL_OPTION, null, null,
				    m_game.getTag(PGN.TAG_FEN));
			    if (fen != null) {
				m_game.setGameByFEN(fen.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setFenMenuItem);
		}
		{
		    JMenuItem setEventDateMenuItem = new JMenuItem("Set event date");
		    setEventDateMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object eventDate = JOptionPane.showInputDialog(GameBrowser.this, "Event date",
				    "Set the event date", JOptionPane.OK_CANCEL_OPTION, null, null,
				    m_game.getTag(PGN.TAG_EVENT_DATE));
			    if (eventDate != null) {
				m_game.setTag(PGN.TAG_EVENT_DATE, eventDate.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setEventDateMenuItem);
		}
		{
		    JMenuItem setWhiteEloMenuItem = new JMenuItem("Set White's ELO");
		    setWhiteEloMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object whiteElo = JOptionPane.showInputDialog(GameBrowser.this, "White's ELO",
				    "Set the White's ELO", JOptionPane.OK_CANCEL_OPTION, null, null,
				    m_game.getTag(PGN.TAG_WHITE_ELO));
			    if (whiteElo != null) {
				m_game.setTag(PGN.TAG_WHITE_ELO, whiteElo.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setWhiteEloMenuItem);
		}
		{
		    JMenuItem setBlackEloMenuItem = new JMenuItem("Set Black's ELO");
		    setBlackEloMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    Object blackElo = JOptionPane.showInputDialog(GameBrowser.this, "Black's ELO",
				    "Set the Black's ELO", JOptionPane.OK_CANCEL_OPTION, null, null,
				    m_game.getTag(PGN.TAG_BLACK_ELO));
			    if (blackElo != null) {
				m_game.setTag(PGN.TAG_BLACK_ELO, blackElo.toString());
				setHeaderLines();
			    }
			}
		    });
		    pgnTagsMenu.add(setBlackEloMenuItem);
		}
		popup.add(pgnTagsMenu);
		popup.addSeparator();
		{
		    JMenuItem promoteVariationItem = new JMenuItem("Promote variation");
		    promoteVariationItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
			    if (!m_game.promoteVariation()) {
				String message = "Ooops. A malfuction happened. Please report the game and move to the program's author.";
				JOptionPane.showMessageDialog(m_parent, message, "Error", JOptionPane.ERROR_MESSAGE);
				return;
			    }
			    m_textViewer.moveModelChanged(null);
			}
		    });
		    popup.add(promoteVariationItem);
		}
		popup.addSeparator();
		{
		    JMenuItem addNullMoveItem = new JMenuItem("Add null move");
		    addNullMoveItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
			    try {
				m_game.getPosition().doMove(Move.NULL_MOVE);
				m_game.getPosition().firePositionChanged(); // TN: new, but is this strictly correct?
			    } catch (IllegalMoveException e) {
				e.printStackTrace();
			    }
			}
		    });
		    popup.add(addNullMoveItem);
		}
		popup.addSeparator();
		{
		    JMenuItem deleteRemainingMovesMenuItem = new JMenuItem("Delete remaining moves");
		    deleteRemainingMovesMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.deleteRemainingMoves();
			    m_textViewer.showCurrentGameNode();
			}
		    });
		    popup.add(deleteRemainingMovesMenuItem);
		}
		{
		    JMenuItem deleteVariationMenuItem = new JMenuItem("Delete this variation");
		    deleteVariationMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.deleteCurrentLine();
			    m_textViewer.showCurrentGameNode();
			}
		    });
		    popup.add(deleteVariationMenuItem);
		}
		popup.addSeparator();
		{
		    JMenuItem deleteAllCommentsMenuItem = new JMenuItem("Strip all comments");
		    deleteAllCommentsMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.removeAllComments();
			    m_textViewer.showCurrentGameNode();
			}
		    });
		    popup.add(deleteAllCommentsMenuItem);
		}
		{
		    JMenuItem deleteAllVariationsMenuItem = new JMenuItem("Strip all variations");
		    deleteAllVariationsMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.deleteAllLines();
			    m_textViewer.showCurrentGameNode();
			}
		    });
		    popup.add(deleteAllVariationsMenuItem);
		}
		{
		    JMenuItem deleteAllNagsMenuItem = new JMenuItem("Strip all NAGs (!?, +-, etc)");
		    deleteAllNagsMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.removeAllNags();
			    m_textViewer.showCurrentGameNode();
			}
		    });
		    popup.add(deleteAllNagsMenuItem);
		}
		{
		    JMenuItem stripAllMenuItem = new JMenuItem("Strip all");
		    stripAllMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
			    m_game.stripAll();
			    m_textViewer.showCurrentGameNode();
			}
		    });
		    popup.add(stripAllMenuItem);
		}
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
