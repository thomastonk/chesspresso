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

import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.EditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;

//import chesspresso.*;
import chesspresso.game.Game;
import chesspresso.game.GameListener;
import chesspresso.game.GameModelChangeListener;
import chesspresso.move.Move;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.NAG;
import chesspresso.position.PositionChangeListener;

/**
 * Textual representation of a game on a panel.
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.2 $
 */
@SuppressWarnings("serial")
public class GameTextViewer extends JEditorPane
	implements GameListener, PositionChangeListener, GameModelChangeListener {

    // attributes for main line
    private static SimpleAttributeSet MAIN = new SimpleAttributeSet();

    // attributes for NAGSs
    private static SimpleAttributeSet NAG_SET = new SimpleAttributeSet();

    // attributes for second NAGSs
    private static SimpleAttributeSet NAG_SET_EXTRA = new SimpleAttributeSet();

    // attributes for comments
    private static SimpleAttributeSet COMMENT = new SimpleAttributeSet();

    // attributes for lines
    private static SimpleAttributeSet LINE = new SimpleAttributeSet();

    static {
	// TODO take some parameters from actual editor pane instance
	// (e.g. font, font size)

	StyleConstants.setForeground(MAIN, Color.black);
	StyleConstants.setBold(MAIN, true);
	StyleConstants.setFontFamily(MAIN, "Arial");
	StyleConstants.setFontSize(MAIN, 12);

	StyleConstants.setForeground(NAG_SET, Color.black);
	StyleConstants.setFontFamily(NAG_SET, "Arial");
	StyleConstants.setFontSize(NAG_SET, 12);

	StyleConstants.setForeground(NAG_SET_EXTRA, Color.red);
	StyleConstants.setFontFamily(NAG_SET_EXTRA, "Serif");
	StyleConstants.setFontSize(NAG_SET_EXTRA, 12);

	StyleConstants.setForeground(COMMENT, Color.black);
	StyleConstants.setFontFamily(COMMENT, "Serif");
	StyleConstants.setFontSize(COMMENT, 12);
	StyleConstants.setItalic(COMMENT, true);

	StyleConstants.setForeground(LINE, Color.black);
	StyleConstants.setFontFamily(LINE, "Serif");
	StyleConstants.setFontSize(LINE, 12);

    }

    // ======================================================================

    private Game m_game;
    private int[] m_moveBegin, m_moveEnd;
    private int[] m_moveNode;

    // ======================================================================

    /**
     * Create a text viewer for the given game
     *
     * @param game the game to represent
     */
    public GameTextViewer(Game game) {
	EditorKit editorKit = new StyledEditorKit();
	setEditorKit(editorKit);
	setEditable(false);

	setSelectionColor(Color.darkGray);
	setSelectedTextColor(Color.white);

	setGame(game);

	addMouseListener(new MouseAdapter() {
	    @Override
	    public void mouseClicked(MouseEvent e) {
		if (!m_userActionEnabled) {
		    return;
		}
		getCaret().setMagicCaretPosition(e.getPoint());
		gotoPlyForCaret();
	    }
	});

	addKeyListener(new KeyAdapter() {
	    @Override
	    public void keyReleased(KeyEvent e) {
		if (!m_userActionEnabled) {
		    return;
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL)
		    return; // =====>
		if (e.getKeyCode() == KeyEvent.VK_SHIFT)
		    return; // =====>
		if (e.getKeyCode() == KeyEvent.VK_ALT)
		    return; // =====>
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
		    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)
			goBackToLineBegin();
		    else
			goBackward();
		} else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
		    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)
			gotoEndOfLine();
		    else if ((e.getModifiers() & KeyEvent.SHIFT_MASK) != 0)
			goForwardMainLine();
		    else
			goForward();
		} else if (e.getKeyCode() == KeyEvent.VK_DOWN)
		    gotoPlyForCaret();
		else if (e.getKeyCode() == KeyEvent.VK_UP)
		    gotoPlyForCaret();
		else if (e.getKeyCode() == KeyEvent.VK_HOME)
		    goStart();
		else if (e.getKeyCode() == KeyEvent.VK_END)
		    goEnd();
		else
		    gotoPlyForCaret();
		if (getSelectionStart() == getSelectionEnd()) // assure that we always have a selection
		    showCurrentGameNode();
	    }
	});

	requestFocus();
    }

    // ======================================================================

    public void setGame(Game game) {
	if (game != null) {
	    if (m_game != null) {
		m_game.getPosition().removePositionChangeListener(this);
		m_game.removeChangeListener(this);
	    }
	    m_game = game;
	    setText("");
	    createText();
	    setCaretPosition(getDocument().getStartPosition().getOffset());
	    m_game.getPosition().addPositionChangeListener(this);
	    m_game.addChangeListener(this);
	}
    }

    // ======================================================================
    // Methods to implement GameModelChangeListener

    @Override
    public void headerModelChanged(Game game) {
	setDocument(new DefaultStyledDocument());
	createText();
    }

    @Override
    public void moveModelChanged(Game game) {
	setDocument(new DefaultStyledDocument());
	createText();
	showCurrentGameNode();
    }

    // ======================================================================

    private static final DefaultHighlighter.DefaultHighlightPainter highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(
	    Color.LIGHT_GRAY);

    void showCurrentGameNode() {
	int node = m_game.getCurNode();
	if (node <= 0) {
	    setCaretPosition(getDocument().getStartPosition().getOffset());
	} else {
	    for (int i = 0; i < m_moveNode.length; i++) {
		if (m_moveNode[i] >= node) {
		    // setCaretPosition(m_moveBegin[i]); // Old code: highlighting via selection.
		    // moveCaretPosition(m_moveEnd[i]); // Drawback: invisible, when focus is lost.

		    setCaretPosition(m_moveBegin[i]); // Do not delete next two lines, because they scroll forward!
		    setCaretPosition(m_moveEnd[i]);
		    getHighlighter().removeAllHighlights();
		    try {
			getHighlighter().addHighlight(m_moveBegin[i], m_moveEnd[i], highlightPainter);
		    } catch (BadLocationException ignore) {
		    }
		    break;
		}
	    }
	}
    }

    // Methods to implement PositionChangeListener

    @Override
    public void notifyPositionChanged(ImmutablePosition position) {
	// It shall not be allowed to replace the position!
    }

    @Override
    public void notifyMoveDone(ImmutablePosition position, short move) {
	requestFocus();
	showCurrentGameNode();
    }

    @Override
    public void notifyMoveUndone(ImmutablePosition position) {
	requestFocus();
	showCurrentGameNode();
    }

    // ======================================================================

    /**
     * Append the text to the document with given attributes.
     *
     * @param text the text to append
     * @param set  the text attributes
     */
    private void appendText(String text, AttributeSet set) {
	try {
	    getDocument().insertString(getDocument().getLength(), text, set);
	} catch (Exception e) {
	    System.out.println(e.getMessage());
	}
    }

    // ======================================================================
    // Methods to implement GameListener

    // state to indicate whether a move number will be needed for the next move
    private boolean m_needsMoveNumber;

    // current move index
    private int m_notifyIndex;

    @Override
    public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber,
	    int level) {
	AttributeSet attrs = (level == 0 ? MAIN : LINE);

	/*---------- pre-move comment -----*/
	if (preMoveComment != null)
	    appendText(preMoveComment + " ", COMMENT);

	/*---------- move number ----------*/
	if (m_needsMoveNumber) {
	    if (move.isWhiteMove()) {
		appendText(((plyNumber + 2) / 2) + ". ", attrs);
	    } else {
		appendText(((plyNumber + 2) / 2) + "... ", attrs);
	    }
	}

	/*---------- move text ----------*/
	m_moveNode[m_notifyIndex] = m_game.getCurNode();
	m_moveBegin[m_notifyIndex] = getDocument().getEndPosition().getOffset() - 1;
	appendText(move.toString(), attrs);

	/*---------- nags ----------*/
	if (nags != null) {
	    for (int i = 0; i < nags.length; i++) {
		if (nags[i] < NAG.NAG_BOUND) {
		    appendText(NAG.getShortString((short) nags[i], false) + " ", NAG_SET);
		} else {
		    // all non-standard nags shall be highlighted!
		    appendText(NAG.getShortString((short) nags[i], false) + " ", NAG_SET_EXTRA);
		}
	    }
	} else {
	    appendText(" ", attrs);
	}
	m_moveEnd[m_notifyIndex] = getDocument().getEndPosition().getOffset() - 2;

	/*---------- pre-move comment -----*/
	if (postMoveComment != null)
	    appendText(postMoveComment + " ", COMMENT);

	m_notifyIndex++;

	m_needsMoveNumber = !move.isWhiteMove() || (postMoveComment != null);
    }

    @Override
    public void notifyLineStart(int level) {
	appendText(" (", LINE);
	m_needsMoveNumber = true;
    }

    @Override
    public void notifyLineEnd(int level) {
	appendText(") ", LINE);
	m_needsMoveNumber = true;
    }

    /**
     * (Re)Create the game text based on the current game.
     */
    private synchronized void createText() {
	int totalPlies = m_game.getTotalNumOfPlies();
	m_moveBegin = new int[totalPlies];
	m_moveEnd = new int[totalPlies];
	m_moveNode = new int[totalPlies];
	m_notifyIndex = 0;

	if (totalPlies == 0) {
	    String emptyGameComment = m_game.getEmptyGameComment();
	    if (emptyGameComment != null && !emptyGameComment.isEmpty()) {
		appendText(emptyGameComment + " ", COMMENT);
	    }
	}

	m_needsMoveNumber = true;
//        // first move's leading comment - why is this necessary? TODO
//        String comment = m_game.getPreMoveComment();
//        if (comment != null)
//            appendText(comment + " ", COMMENT);

	m_game.traverse(this, true);
	appendText(m_game.getResultStr(), MAIN);
    }

    // ======================================================================
    // Methods to walk through the game

    private boolean goBackward() {
	boolean retVal = m_game.goBack();
	m_game.getPosition().firePositionChanged();
	return retVal;
    }

    private void goBackToLineBegin() {
	m_game.goBackToLineBegin();
	m_game.getPosition().firePositionChanged();
    }

    private void gotoEndOfLine() {
	m_game.gotoEndOfLine();
	m_game.getPosition().firePositionChanged();
    }

    private void goForwardMainLine() {
	m_game.goForward(0);
	m_game.getPosition().firePositionChanged();
    }

    private boolean goForward() {
	int num = m_game.getNumOfNextMoves();
	boolean retVal = false;
	if (num > 1) {
	    retVal = m_game.goForward(0);
	} else if (num == 1) {
	    retVal = m_game.goForward();
	}
	m_game.getPosition().firePositionChanged();
	return retVal;
    }

    private void goStart() {
	m_game.gotoStart();
	m_game.getPosition().firePositionChanged();
    }

    private void goEnd() {
	m_game.gotoEndOfLine();
	m_game.getPosition().firePositionChanged();
    }

    private int getNodeForCaret() {
	int caret = getCaretPosition();
	if (caret == 0)
	    return m_game.getRootNode();
	for (int i = 0; i < m_moveNode.length - 1; i++) { // TODO: bin search?
	    if (m_moveBegin[i + 1] > caret)
		return m_moveNode[i];
	}
	if (m_moveNode.length == 0) {
	    return m_game.getRootNode();
	} else {
	    return m_moveNode[m_moveNode.length - 1];
	}
    }

    void gotoPlyForCaret() {
	m_game.gotoNode(getNodeForCaret());
	m_game.getPosition().firePositionChanged();
    }

    private boolean m_userActionEnabled = true;

    public void setUserActionEnabled(final boolean userActionEnabled) {
	m_userActionEnabled = userActionEnabled;
    }

    private class FocusRequester implements FocusListener {
	@Override
	public void focusLost(FocusEvent e) {
	    SwingUtilities.invokeLater(() -> requestFocusInWindow());
	}

	@Override
	public void focusGained(FocusEvent e) {
	}
    }

    void setFocusRequesting(boolean request) {
	FocusListener[] listeners = getFocusListeners();
	for (int i = 0; i < listeners.length; ++i) {
	    if (listeners[i] != null && listeners[i] instanceof FocusRequester) {
		removeFocusListener(listeners[i]);
	    }
	}
	if (request) {
	    addFocusListener(new FocusRequester());
	}
    }

}
