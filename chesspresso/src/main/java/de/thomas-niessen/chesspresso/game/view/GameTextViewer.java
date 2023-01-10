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

import java.awt.Color;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import javax.swing.JEditorPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;

import chesspresso.game.Game;
import chesspresso.game.GameModelChangeListener;
import chesspresso.game.TraverseListener;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.position.NAG;
import chesspresso.position.Position;
import chesspresso.position.PositionListener;

/**
 * Textual representation of a game on a panel.
 *
 * @author Bernhard Seybold
 * 
 */
@SuppressWarnings("serial")
public class GameTextViewer extends JEditorPane implements PositionListener, GameModelChangeListener {

	// attributes for main line
	private static final SimpleAttributeSet MAIN = new SimpleAttributeSet();

	// attributes for NAGs
	private static final SimpleAttributeSet NAG_SET = new SimpleAttributeSet();

	// attributes for second NAGs
	private static final SimpleAttributeSet NAG_SET_EXTRA = new SimpleAttributeSet();

	// attributes for comments
	private static final SimpleAttributeSet COMMENT = new SimpleAttributeSet();

	// attributes for lines
	private static final SimpleAttributeSet LINE = new SimpleAttributeSet();

	private static final String startSymbol;

	static {
		String fontFamily;
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			fontFamily = "Arial";
			startSymbol = "\u25BA "; // This arrow and the triangle \u2586 don't work or don't look good with font
			// 'Dialog'. The upwards triangle \u25B2 is a better choice then.
		} else {
			fontFamily = "Dialog";
			startSymbol = "\u25B2 ";
		}
		int fontSize = 12;
		StyleConstants.setForeground(MAIN, Color.black);
		StyleConstants.setBold(MAIN, true);
		StyleConstants.setFontFamily(MAIN, fontFamily); // war Arial
		StyleConstants.setFontSize(MAIN, fontSize);

		StyleConstants.setForeground(NAG_SET, Color.black);
		StyleConstants.setFontFamily(NAG_SET, fontFamily);
		StyleConstants.setFontSize(NAG_SET, fontSize);

		StyleConstants.setForeground(NAG_SET_EXTRA, Color.red);
		StyleConstants.setFontFamily(NAG_SET_EXTRA, fontFamily); // war Serif
		StyleConstants.setFontSize(NAG_SET_EXTRA, fontSize);

		StyleConstants.setForeground(COMMENT, Color.black);
		StyleConstants.setFontFamily(COMMENT, fontFamily); // war Serif
		StyleConstants.setFontSize(COMMENT, fontSize);
		//	StyleConstants.setItalic(COMMENT, true);

		StyleConstants.setForeground(LINE, Color.black);
		StyleConstants.setFontFamily(LINE, fontFamily); // war Serif
		StyleConstants.setFontSize(LINE, fontSize);
	}

	// ======================================================================

	public enum TextCreationType {
		COMPACT("Compact"), TREE_LIKE("Tree-like"), PUZZLE_MODE("Puzzle mode");

		String description;

		TextCreationType(String desc) {
			description = desc;
		}

		@Override
		public String toString() {
			return description;
		}

		public static TextCreationType getType(String desc) {
			for (TextCreationType type : TextCreationType.values()) {
				if (type.description.equals(desc)) {
					return type;
				}
			}
			return null;
		}
	}

	// ======================================================================

	private Game m_game;
	private TraverseListener textCreator;
	private UserAction m_userAction;
	private int[] m_moveBegin, m_moveEnd;
	private int[] m_moveNrBegin;
	private int[] m_moveNode;

	// ======================================================================

	/**
	 * Create a text viewer for the given game
	 *
	 * @param game the game to represent
	 */
	public GameTextViewer(Game game, UserAction userAction) {
		EditorKit editorKit = new StyledEditorKit();
		setEditorKit(editorKit);
		setEditable(false);

		setSelectionColor(Color.darkGray);
		setSelectedTextColor(Color.white);

		textCreator = new TreeLikeTextCreator(); // has to be set before setGame()!

		setGame(game); // sets also m_game
		m_userAction = userAction;

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if ((m_userAction != UserAction.ENABLED) || SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()
						|| (e.getPoint().y < 3)) { // clicks at the upper boundary move otherwise the game to first ply
					return;
				}
				try {
					// ignore clicks into the empty space at the bottom
					Rectangle2D rect = modelToView2D(getDocument().getLength());
					if (rect != null) {
						if ((rect.getY() + rect.getHeight() < e.getY()) || (rect.getY() < e.getY() && rect.getX() < e.getX())) {
							return;
						}
					}
				} catch (BadLocationException ignore) {
				}
				getCaret().setMagicCaretPosition(e.getPoint()); // TN: Isn't this useless?
				gotoPlyForCaret();
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_CONTROL || e.getKeyCode() == KeyEvent.VK_SHIFT
						|| e.getKeyCode() == KeyEvent.VK_ALT) {
					return;
				}
				if (e.getKeyCode() == KeyEvent.VK_LEFT) {
					if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
						goBackToLineBegin();
						centerLineInScrollPane(GameTextViewer.this);
					} else {
						goBackward();
						centerLineInScrollPane(GameTextViewer.this);
					}
				} else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
					if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
						gotoEndOfLine();
						centerLineInScrollPane(GameTextViewer.this);
					} else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
						goForwardMainLine();
						centerLineInScrollPane(GameTextViewer.this);
					} else {
						goForward();
						centerLineInScrollPane(GameTextViewer.this);
					}
				} else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					gotoPlyForCaret();
					centerLineInScrollPane(GameTextViewer.this);
				} else if (e.getKeyCode() == KeyEvent.VK_UP) {
					gotoPlyForCaret();
					centerLineInScrollPane(GameTextViewer.this);
				} else if (e.getKeyCode() == KeyEvent.VK_HOME) {
					goStart();
				} else if (e.getKeyCode() == KeyEvent.VK_END) {
					goEnd();
				} else {
					gotoPlyForCaret();
				}
				if (getSelectionStart() == getSelectionEnd()) { // assure that we always have a selection
					showCurrentGameNode();
				}
			}
		});

		requestFocusInWindow();
	}

	// ======================================================================

	public void setGame(Game game) {
		if (game != null) {
			if (m_game != null) {
				m_game.getPosition().removePositionListener(this);
				m_game.removeChangeListener(this);
			}
			m_game = game;
			createText();
			setCaretPosition(getDocument().getStartPosition().getOffset());
			m_game.getPosition().addPositionListener(this);
			m_game.addChangeListener(this);
		}
	}

	public TextCreationType getTextCreationType() {
		if (textCreator instanceof CompactTextCreator) {
			return TextCreationType.COMPACT;
		} else if (textCreator instanceof TreeLikeTextCreator) {
			return TextCreationType.TREE_LIKE;
		} else if (textCreator instanceof PuzzleModeTextCreator) {
			return TextCreationType.PUZZLE_MODE;
		}
		throw new IllegalStateException("GameTextViewer::getTextCreationType: unknown text creator.");
	}

	public void setTextCreationType(TextCreationType type) {
		switch (type) {
		case COMPACT:
			textCreator = new CompactTextCreator();
			break;
		case TREE_LIKE:
			textCreator = new TreeLikeTextCreator();
			break;
		case PUZZLE_MODE:
			textCreator = new PuzzleModeTextCreator();
			break;
		default:
			throw new IllegalArgumentException("GameTextViewer::setTextCreationType: " + type);
		}
		createText();
	}

	public void setUserAction(UserAction userAction) {
		m_userAction = userAction;
	}

	// ======================================================================
	// Methods to implement GameModelChangeListener

	@Override
	public void headerModelChanged(Game game) {
		// Do not add an unnecessary 
		// createText();
		// here. A header model change can be an FEN tag change, and hence the header and the
		// move model can be inconsistent, and thus createText can fail. 
	}

	@Override
	public void moveModelChanged(Game game) {
		createText();
		showCurrentGameNode();
	}

	// ======================================================================

	private static final DefaultHighlighter.DefaultHighlightPainter highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(
			Color.LIGHT_GRAY);

	void showCurrentGameNode() {
		int node = m_game.getCurNode();
		int index = -1;
		if (node > 0) {
			for (int i = 0; i < m_moveNode.length; i++) {
				if (m_moveNode[i] >= node) {
					index = i;
					break;
				}
			}
		}
		getHighlighter().removeAllHighlights();
		if (index >= 0) {
			setCaretPosition(m_moveBegin[index]); // Do not delete next two lines, because they scroll forward!
			setCaretPosition(m_moveEnd[index]);
			try {
				getHighlighter().addHighlight(m_moveBegin[index], m_moveEnd[index], highlightPainter);
			} catch (BadLocationException ignore) {
			}
		} else if (node == 0 && m_moveBegin.length > 0) {
			// Highlight the triangle if and only if the start position is shown (node = 0)
			// and the triangle itself is shown (m_moveBegin.length > 0, see createText()).
			setCaretPosition(0); // Do not delete next two lines, because they scroll forward!
			setCaretPosition(1);
			try {
				getHighlighter().addHighlight(0, 1, highlightPainter);
			} catch (BadLocationException ignore) {
			}

		}
	}

	// PositionListener

	@Override
	public void positionChanged(Position pos) {
		requestFocusInWindow();
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
	// TraverseListeners for text creation

	// indicate whether a move number will be needed for the next move
	private boolean m_needsMoveNumber;
	// current move index
	private int m_notifyIndex;

	private abstract class AbstractTextCreator implements TraverseListener {

		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level) {
			AttributeSet attrs = (level == 0 ? MAIN : LINE);

			/*---------- pre-move comment -----*/
			if (preMoveComment != null) {
				appendText(preMoveComment + " ", COMMENT);
			}

			/*---------- begin of move number or move -----*/
			m_moveNrBegin[m_notifyIndex] = getDocument().getEndPosition().getOffset() - 1;

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
				for (short nag : nags) {
					if (nag < NAG.NAG_BOUND) {
						appendText(NAG.getShortString(nag, false) + " ", NAG_SET);
					} else {
						// all non-standard nags shall be highlighted!
						appendText(NAG.getShortString(nag, false) + " ", NAG_SET_EXTRA);
					}
				}
			} else {
				appendText(" ", attrs);
			}
			m_moveEnd[m_notifyIndex] = getDocument().getEndPosition().getOffset() - 2;

			/*---------- post-move comment -----*/
			if (postMoveComment != null) {
				appendText(postMoveComment + " ", COMMENT);
			}

			m_notifyIndex++;

			m_needsMoveNumber = !move.isWhiteMove() || (postMoveComment != null);
		}
	}

	private class CompactTextCreator extends AbstractTextCreator {

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
	}

	private class TreeLikeTextCreator extends AbstractTextCreator {

		private boolean m_newLineNeeded;

		@Override
		public void initTraversal() {
			m_newLineNeeded = false;
		}

		private void indent(int level) {
			for (int i = 0; i < level; ++i) {
				appendText("   ", LINE);
			}
		}

		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level) {
			if (m_newLineNeeded) {
				appendText(System.lineSeparator(), LINE);
				indent(level - 1);
				m_newLineNeeded = false;
			}
			super.notifyMove(move, nags, preMoveComment, postMoveComment, plyNumber, level);
		}

		@Override
		public void notifyLineStart(int level) {
			appendText(System.lineSeparator(), LINE);
			indent(level);
			appendText("(", LINE);
			m_needsMoveNumber = true;
			m_newLineNeeded = false;
		}

		@Override
		public void notifyLineEnd(int level) {
			appendText(") ", LINE);
			m_needsMoveNumber = true;
			m_newLineNeeded = true;
		}
	}

	private class PuzzleModeTextCreator implements TraverseListener {

		private int maxPly;
		private boolean stopRequested;
		private final TreeLikeTextCreator textCreator;
		private String startFen; // The game is changed by deep copying, so the start FEN is used 
									//for game change detection

		PuzzleModeTextCreator() {
			stopRequested = false;
			startFen = m_game.getTag(PGN.TAG_FEN); // can be null, if this is no puzzle
			if (startFen != null && m_game.getNumOfPlies() > 0) { // a puzzle
				maxPly = m_game.getPlyOffset() + 1;
			} else { // no puzzle
				maxPly = m_game.getNumOfPlies() + 1;
			}
			textCreator = new TreeLikeTextCreator();
			m_game.getPosition().addPositionListener(e -> {
				// this listener updates maxPly
				if (m_game.isMainLine()) { // Is there a new mainline move?
					if (maxPly < m_game.getCurrentPly()) {
						maxPly = m_game.getCurrentPly();
						createText();
					}
				} else { // Is there a new sub-variation move with missing mainline move? 
					Game copy = m_game.getDeepCopy();
					copy.gotoNode(m_game.getCurNode());
					if (copy.goBack() && copy.goForward()) {
						if (copy.isMainLine() && maxPly < copy.getCurrentPly()) {
							maxPly = copy.getCurrentPly();
							createText();
						}
					}
				}
			});
		}

		@Override
		public void initTraversal() {
			stopRequested = false;
			if (!Objects.equals(startFen, m_game.getTag(PGN.TAG_FEN))) { // the game has changed
				startFen = m_game.getTag(PGN.TAG_FEN);
				if (startFen != null) { // puzzle mode
					maxPly = m_game.getPlyOffset() + 1;
				} else {
					maxPly = m_game.getNumOfPlies() + 1;
				}
			}
			textCreator.initTraversal();
		}

		@Override
		public boolean stopRequested() {
			return stopRequested;
		}

		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level) {
			if (plyNumber >= maxPly && level == 0) {
				stopRequested = true;
				return;
			}
			textCreator.notifyMove(move, nags, preMoveComment, postMoveComment, plyNumber, level);
		}

		@Override
		public void notifyLineStart(int level) {
			textCreator.notifyLineStart(level);
		}

		@Override
		public void notifyLineEnd(int level) {
			textCreator.notifyLineEnd(level);
		}
	}

	/**
	 * Create or recreate the game text based on the current game.
	 */
	private synchronized void createText() {
		if (!SwingUtilities.isEventDispatchThread()) {
			try {
				SwingUtilities.invokeAndWait(() -> createTextOnEDT());
			} catch (InvocationTargetException | InterruptedException e) {
				System.err.println("GameTextViewer::createText: " + e);
				e.printStackTrace();
			}
		} else {
			createTextOnEDT();
		}
	}

	private void createTextOnEDT() {
		setDocument(new DefaultStyledDocument());
		// This is better than setText(""); see documentation of JEditorPane::setText(),
		// which suggests
		// setDocument(getEditorKit().createDefaultDocument());
		// but this should be equivalent.

		int totalPlies = m_game.getTotalNumOfPlies();
		m_moveBegin = new int[totalPlies];
		m_moveEnd = new int[totalPlies];
		m_moveNrBegin = new int[totalPlies];
		m_moveNode = new int[totalPlies];
		m_notifyIndex = 0;

		if (totalPlies == 0) {
			String emptyGameComment = m_game.getEmptyGameComment();
			if (emptyGameComment != null && !emptyGameComment.isEmpty()) {
				appendText(emptyGameComment + " ", COMMENT);
			}
		} else {
			appendText(startSymbol, MAIN);
		}

		m_needsMoveNumber = true;
		m_game.traverse(textCreator, true);
		if (!(textCreator instanceof PuzzleModeTextCreator)) {
			if (textCreator instanceof TreeLikeTextCreator && getDocument().getLength() > 0) {
				appendText(System.lineSeparator(), MAIN);
			}
			appendText(m_game.getResultStr(), MAIN);
		}

		// TN:
		// Unfortunately, there appears sometimes a line-break within the result. This could be
		// avoided by replacing the above line by the following line. But this looks more often
		// weird, too. So, a perfect solution would be to decide, when the new line is 
		// necessary. Can this be done somehow? It should work, if the component is resized, too. 
		//		appendText(System.lineSeparator() + m_game.getResultStr(), MAIN);

		// One possible solution:
		// 1. determine the number of lines before the result string
		//    (see https://stackoverflow.com/questions/13807575/how-to-get-the-number-of-lines-from-a-jtextpane)
		// 2. add the result string without line separator and check whether the number of lines has changed,
		// 3. if so, then create the text again and this time add the line separator.

		// Okay, this does not avoid split castling strings. Have I ever seen such? YES! This happens
		// with long and short castles.

		// Alternative JTextArea:
		// Pros: has wrap-style word. Cons: Does not support setEditorKit and hence looks poor. (HTML?)

		// Alternative JTextPane:
		// Looks like JeditorPane, but does not support wrap-style word.
	}

	// ======================================================================
	// Methods to walk through the game

	private boolean goBackward() {
		if (m_userAction == UserAction.ENABLED || m_userAction == UserAction.NAVIGABLE) {
			return m_game.goBack();
		}
		return false;
	}

	private void goBackToLineBegin() {
		if (m_userAction == UserAction.ENABLED || m_userAction == UserAction.NAVIGABLE) {
			m_game.goBackToLineBegin();
		}
	}

	private void gotoEndOfLine() {
		if (m_userAction == UserAction.ENABLED || m_userAction == UserAction.NAVIGABLE) {
			m_game.gotoEndOfLine();
		}
	}

	private void goForwardMainLine() {
		if (m_userAction == UserAction.ENABLED || m_userAction == UserAction.NAVIGABLE) {
			m_game.goForward(0);
		}
	}

	private boolean goForward() {
		if (m_userAction != UserAction.ENABLED && m_userAction != UserAction.NAVIGABLE) {
			return false;
		}
		int num = m_game.getNumOfNextMoves();
		boolean retVal = false;
		if (num > 1) {
			retVal = m_game.goForward(0);
		} else if (num == 1) {
			retVal = m_game.goForward();
		}
		return retVal;
	}

	private void goStart() {
		if (m_userAction == UserAction.ENABLED || m_userAction == UserAction.NAVIGABLE) {
			m_game.gotoStart();
		}
	}

	private void goEnd() {
		if (m_userAction == UserAction.ENABLED || m_userAction == UserAction.NAVIGABLE) {
			m_game.gotoEndOfLine();
		}
	}

	private int getNodeForCaret() {
		int caret = getCaretPosition();
		if (caret < 3) {
			return m_game.getRootNode();
		}
		for (int i = 0; i < m_moveNode.length - 1; i++) {
			if (m_moveNrBegin[i + 1] > caret) {
				return m_moveNode[i];
			}
		}
		if (m_moveNode.length == 0) {
			return m_game.getRootNode();
		} else {
			// Here we need the last node. The old 
			// return m_moveNode[m_moveNode.length - 1];
			// does not work in puzzle mode, because m_moveNode is not completely filled until all moves are shown.
			int j = m_moveNode.length - 1;
			while (m_moveNode[j] == 0 && j > 0) {
				--j;
			}
			return m_moveNode[j];
		}
	}

	private void gotoPlyForCaret() {
		int newNode = getNodeForCaret();
		if (m_game.getCurNode() != newNode) {
			m_game.gotoNode(newNode);
		}
	}

	private class FocusRequester implements FocusListener {
		@Override
		public void focusLost(FocusEvent e) {
			SwingUtilities.invokeLater(GameTextViewer.this::requestFocusInWindow);
		}

		@Override
		public void focusGained(FocusEvent e) {
		}
	}

	void setFocusRequesting(boolean request) {
		FocusListener[] listeners = getFocusListeners();
		for (FocusListener listener : listeners) {
			if (listener instanceof FocusRequester) {
				removeFocusListener(listener);
			}
		}
		if (request) {
			addFocusListener(new FocusRequester());
		}
	}

	/* Taken from Rob Camick's posting (2 January 2009) https://tips4java.wordpress.com/2009/01/04/center-line-in-scroll-pane/
	 * which is free to use: https://tips4java.wordpress.com/about/
	 * More about this topic
	 * https://stackoverflow.com/questions/6056376/how-do-i-center-the-caret-position-of-a-jtextpane-by-autoscrolling
	 * 
	 * Modified because modelToView is deprecated since Java 9.
	 */
	static void centerLineInScrollPane(JTextComponent component) {
		Container container = SwingUtilities.getAncestorOfClass(JViewport.class, component);
		if (container == null) {
			return;
		}
		try {
			Rectangle2D r2D = component.modelToView2D(component.getCaretPosition());
			if (r2D instanceof Rectangle r) {
				JViewport viewport = (JViewport) container;
				int extentHeight = viewport.getExtentSize().height;
				int viewHeight = viewport.getViewSize().height;

				int y = Math.max(0, r.y - ((extentHeight - r.height) / 2));
				y = Math.min(y, viewHeight - extentHeight);

				viewport.setViewPosition(new Point(0, y));
			} else {
				System.err.println("GameTextViewer::centerLineInScrollPane: unexpected rectangle class.");
			}
		} catch (BadLocationException ble) {
		}
	}

}
