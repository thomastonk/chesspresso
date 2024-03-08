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
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
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
import javax.swing.text.Utilities;

import chesspresso.game.Game;
import chesspresso.game.GameModelChangeListener;
import chesspresso.game.TraverseListener;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.position.NAG;
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

	private static final String START_SYMBOL;

	static {
		String fontFamily;
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			fontFamily = "Arial";
			START_SYMBOL = "\u25BA "; // This arrow and the triangle \u2586 don't work or don't look good with font
			// 'Dialog'. The upwards triangle \u25B2 is a better choice then.
		} else {
			fontFamily = "Dialog";
			START_SYMBOL = "\u25B2 ";
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

		final String description;

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

	private Game game;
	private TraverseListener textCreator;
	private UserAction userAction;
	private int[] moveBegin, moveEnd;
	private int[] moveNrBegin;
	private int[] moveNode;

	// ======================================================================

	/**
	 * Create a text viewer for the given game
	 *
	 * @param game the game to represent
	 */
	public GameTextViewer(Game game, UserAction userAction, Component parent) {
		EditorKit editorKit = new StyledEditorKit();
		setEditorKit(editorKit);
		setEditable(false);

		setSelectionColor(Color.darkGray);
		setSelectedTextColor(Color.white);

		textCreator = new TreeLikeTextCreator(); // has to be set before setGame()!

		setGame(game); // sets also this.game
		this.userAction = userAction;

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if ((userAction != UserAction.ENABLED) || (e.getPoint().y < 3)) {
					// Ignore clicks at the upper boundary, since they always move the game to the first ply.
					return;
				}
				int vOffset = 0;
				if (!SwingUtilities.isRightMouseButton(e) || isEventWithinMoves(e)) {
					int rightClickCaretPosition = viewToModel2D(e.getPoint());
					setCaretPosition(rightClickCaretPosition);
					gotoPlyForCaret();
					vOffset = 10; // a selected move will stay visible when the popup is shown
				}
				if (SwingUtilities.isRightMouseButton(e)) {
					TextViewerPopup popup = new TextViewerPopup(game, GameTextViewer.this, parent);
					popup.show(GameTextViewer.this, e.getX(), e.getY() + vOffset);
				}
			}

			private boolean isEventWithinMoves(MouseEvent e) {
				Point point = e.getPoint();
				Rectangle2D rect;
				try {
					int pos = viewToModel2D(point);
					for (int index = 0; index < moveBegin.length; ++index) {
						// This for-loop ensures that only a click to a move, but not to a comment, to
						// a move number or to an indentation at begin of the line counts. 
						if (moveBegin[index] <= pos && pos <= moveEnd[index]) {
							rect = modelToView2D(pos);
							rect.setRect(rect.getX() - 5.d, rect.getY(), 10.d, rect.getHeight()); // magical constants
							return rect.contains(point);
						}
					}
				} catch (BadLocationException ignore) {
				}
				return false;
			}
		});

		addKeyBindings();
	}

	// ======================================================================

	private void addKeyBindings() {
		InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = getActionMap();
		{
			GoLeftAction goLeftAction = new GoLeftAction();
			inputMap.put(KeyStroke.getKeyStroke("released LEFT"), "left");
			actionMap.put("left", goLeftAction);
			inputMap.put(KeyStroke.getKeyStroke("control released LEFT"), "control left");
			actionMap.put("control left", goLeftAction);
		}
		{
			GoRightAction goRightAction = new GoRightAction();
			inputMap.put(KeyStroke.getKeyStroke("released RIGHT"), "right");
			actionMap.put("right", goRightAction);
			inputMap.put(KeyStroke.getKeyStroke("control released RIGHT"), "control right");
			actionMap.put("control right", goRightAction);
		}
		{
			inputMap.put(KeyStroke.getKeyStroke("released UP"), "up");
			actionMap.put("up", new GoUpOrDownAction(true));
		}
		{
			inputMap.put(KeyStroke.getKeyStroke("released DOWN"), "down");
			actionMap.put("down", new GoUpOrDownAction(false));
		}
		{
			GoToStartAction goToStartAction = new GoToStartAction();
			inputMap.put(KeyStroke.getKeyStroke("released HOME"), "home");
			actionMap.put("home", goToStartAction);
		}
		{
			GoToEndAction goToEndAction = new GoToEndAction();
			inputMap.put(KeyStroke.getKeyStroke("released END"), "end");
			actionMap.put("end", goToEndAction);
		}
		{
			// Make Ctrl+V available as an accelerator key in the surrounding frame.
			inputMap.put(KeyStroke.getKeyStroke("control released V"), "control V");
			actionMap.put("control V", new AbstractAction() {

				@Override
				public void actionPerformed(ActionEvent e) {
					Window activeWindow = javax.swing.FocusManager.getCurrentManager().getActiveWindow();
					if (activeWindow != null && activeWindow instanceof JFrame frame) {
						JMenuBar menuBar = frame.getJMenuBar();
						if (menuBar != null) {
							for (int index = 0; index < menuBar.getMenuCount(); ++index) {
								JMenu menu = menuBar.getMenu(index);
								for (Component comp : menu.getMenuComponents()) {
									if (comp instanceof JMenuItem item) {
										KeyStroke accelerator = item.getAccelerator();
										if (accelerator != null && accelerator
												.equals(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK))) {
											item.doClick();
											return;
										}
									}
								}
							}
						}
					}
				}
			});
		}
	}

	private class GoLeftAction extends AbstractAction {

		@Override
		public void actionPerformed(ActionEvent e) {
			if ((e.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
				goBackToLineBegin();
				centerLineInScrollPane(GameTextViewer.this);
			} else {
				goBackward();
				centerLineInScrollPane(GameTextViewer.this);
			}
			if (getSelectionStart() == getSelectionEnd()) { // assure that we always have a selection
				showCurrentGameNode();
			}
		}
	}

	private class GoRightAction extends AbstractAction {

		@Override
		public void actionPerformed(ActionEvent e) {
			if ((e.getModifiers() & ActionEvent.CTRL_MASK) != 0) {
				gotoEndOfLine();
				centerLineInScrollPane(GameTextViewer.this);
			} else {
				goForward();
				centerLineInScrollPane(GameTextViewer.this);
			}
			if (getSelectionStart() == getSelectionEnd()) { // assure that we always have a selection
				showCurrentGameNode();
			}
		}
	}

	private class GoUpOrDownAction extends AbstractAction {
		/* This action is performed using the pane's caret, which then is used to find the next node.
		 * There are however cases, when these operations don't work well together. To fix this, it
		 * would be necessary to decouple this action from the pane's caret, which is quite a lot to do. 
		 */

		private final boolean up;

		GoUpOrDownAction(boolean up) {
			this.up = up;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (!hasFocus()) {
				try {
					if (up) {
						setCaretPosition(Utilities.getPositionAbove(GameTextViewer.this, getCaretPosition(),
								(float) modelToView2D(getCaretPosition()).getCenterX()));
					} else {
						setCaretPosition(Utilities.getPositionBelow(GameTextViewer.this, getCaretPosition(),
								(float) modelToView2D(getCaretPosition()).getCenterX()));
					}
				} catch (BadLocationException ignore) {
				}
			}
			gotoPlyForCaret();
			centerLineInScrollPane(GameTextViewer.this);
			if (getSelectionStart() == getSelectionEnd()) { // assure that we always have a selection
				showCurrentGameNode();
			}
		}
	}

	private class GoToStartAction extends AbstractAction {

		@Override
		public void actionPerformed(ActionEvent e) {
			gotoStart();
			if (getSelectionStart() == getSelectionEnd()) { // assure that we always have a selection
				showCurrentGameNode();
			}
		}
	}

	private class GoToEndAction extends AbstractAction {

		@Override
		public void actionPerformed(ActionEvent e) {
			gotoEnd();
			if (getSelectionStart() == getSelectionEnd()) { // assure that we always have a selection
				showCurrentGameNode();
			}
		}
	}

	// ======================================================================

	public void setGame(Game game) {
		if (game != null) {
			if (game != null) {
				game.getPosition().removePositionListener(this);
				game.removeChangeListener(this);
			}
			this.game = game;
			createText();
			setCaretPosition(getDocument().getStartPosition().getOffset());
			game.getPosition().addPositionListener(this);
			game.addChangeListener(this);
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
		case COMPACT -> textCreator = new CompactTextCreator();
		case TREE_LIKE -> textCreator = new TreeLikeTextCreator();
		case PUZZLE_MODE -> textCreator = new PuzzleModeTextCreator();
		default -> throw new IllegalArgumentException("GameTextViewer::setTextCreationType: " + type);
		}
		createText();
	}

	public void setUserAction(UserAction userAction) {
		this.userAction = userAction;
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
		int node = game.getCurNode();
		int index = -1;
		if (node > 0) {
			for (int i = 0; i < moveNode.length; i++) {
				if (moveNode[i] >= node) {
					index = i;
					break;
				}
			}
		}
		getHighlighter().removeAllHighlights();
		if (index >= 0) {
			setCaretPosition(moveBegin[index]); // Do not delete next two lines, because they scroll forward!
			setCaretPosition(moveEnd[index]);
			try {
				getHighlighter().addHighlight(moveBegin[index], moveEnd[index], highlightPainter);
			} catch (BadLocationException ignore) {
			}
		} else if (node == 0 && moveBegin.length > 0) {
			// Highlight the triangle if and only if the start position is shown (node = 0)
			// and the triangle itself is shown (moveBegin.length > 0, see createText()).
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
	public void positionChanged(ChangeType type, short move, String fen) {
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
	private boolean needsMoveNumber;
	// current move index
	private int notifyIndex;

	private abstract class AbstractTextCreator implements TraverseListener {

		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level,
				String fenBeforeMove) {
			AttributeSet attrs = (level == 0 ? MAIN : LINE);

			/*---------- pre-move comment -----*/
			if (preMoveComment != null) {
				appendText(preMoveComment + " ", COMMENT);
			}

			/*---------- begin of move number or move -----*/
			moveNrBegin[notifyIndex] = getDocument().getEndPosition().getOffset() - 1;

			/*---------- move number ----------*/
			if (needsMoveNumber) {
				if (move.isWhiteMove()) {
					appendText(((plyNumber + 2) / 2) + ". ", attrs);
				} else {
					appendText(((plyNumber + 2) / 2) + "... ", attrs);
				}
			}

			/*---------- move text ----------*/
			moveNode[notifyIndex] = game.getCurNode();
			moveBegin[notifyIndex] = getDocument().getEndPosition().getOffset() - 1;
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
			moveEnd[notifyIndex] = getDocument().getEndPosition().getOffset() - 2;

			/*---------- post-move comment -----*/
			if (postMoveComment != null) {
				appendText(postMoveComment + " ", COMMENT);
			}

			notifyIndex++;

			needsMoveNumber = !move.isWhiteMove() || (postMoveComment != null);
		}
	}

	private class CompactTextCreator extends AbstractTextCreator {

		@Override
		public void notifyLineStart(int level) {
			appendText(" (", LINE);
			needsMoveNumber = true;
		}

		@Override
		public void notifyLineEnd(int level) {
			appendText(") ", LINE);
			needsMoveNumber = true;
		}
	}

	private class TreeLikeTextCreator extends AbstractTextCreator {

		private boolean newLineNeeded;

		@Override
		public void initTraversal() {
			newLineNeeded = false;
		}

		private void indent(int level) {
			for (int i = 0; i < level; ++i) {
				appendText("   ", LINE);
			}
		}

		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level,
				String fenBeforeMove) {
			if (newLineNeeded) {
				appendText(System.lineSeparator(), LINE);
				indent(level - 1);
				newLineNeeded = false;
			}
			super.notifyMove(move, nags, preMoveComment, postMoveComment, plyNumber, level, fenBeforeMove);
		}

		@Override
		public void notifyLineStart(int level) {
			appendText(System.lineSeparator(), LINE);
			indent(level);
			appendText("(", LINE);
			needsMoveNumber = true;
			newLineNeeded = false;
		}

		@Override
		public void notifyLineEnd(int level) {
			appendText(") ", LINE);
			needsMoveNumber = true;
			newLineNeeded = true;
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
			startFen = game.getTag(PGN.TAG_FEN); // can be null, if this is no puzzle
			if (startFen != null && game.getNumOfPlies() > 0) { // a puzzle
				maxPly = game.getPlyOffset() + 1;
			} else { // no puzzle
				maxPly = game.getNumOfPlies() + 1;
			}
			textCreator = new TreeLikeTextCreator();
			game.getPosition().addPositionListener((t, f, m) -> {
				// this listener updates maxPly
				if (game.isMainLine()) { // Is there a new mainline move?
					if (maxPly < game.getCurrentPly()) {
						maxPly = game.getCurrentPly();
						createText();
					}
				} else { // Is there a new sub-variation move with missing mainline move? 
					Game copy = game.getDeepCopy();
					copy.gotoNode(game.getCurNode());
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
			if (!Objects.equals(startFen, game.getTag(PGN.TAG_FEN))) { // the game has changed
				startFen = game.getTag(PGN.TAG_FEN);
				if (startFen != null) { // puzzle mode
					maxPly = game.getPlyOffset() + 1;
				} else {
					maxPly = game.getNumOfPlies() + 1;
				}
			}
			textCreator.initTraversal();
		}

		@Override
		public boolean stopRequested() {
			return stopRequested;
		}

		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level,
				String fenBeforeMove) {
			if (plyNumber >= maxPly && level == 0) {
				stopRequested = true;
				return;
			}
			textCreator.notifyMove(move, nags, preMoveComment, postMoveComment, plyNumber, level, fenBeforeMove);
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
				SwingUtilities.invokeAndWait(this::createTextOnEDT);
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

		int totalPlies = game.getTotalNumOfPlies();
		moveBegin = new int[totalPlies];
		moveEnd = new int[totalPlies];
		moveNrBegin = new int[totalPlies];
		moveNode = new int[totalPlies];
		notifyIndex = 0;

		if (totalPlies == 0) {
			String emptyGameComment = game.getEmptyGameComment();
			if (emptyGameComment != null && !emptyGameComment.isEmpty()) {
				appendText(emptyGameComment + " ", COMMENT);
			}
		} else {
			appendText(START_SYMBOL, MAIN);
		}

		needsMoveNumber = true;
		game.traverse(textCreator, true);
		if (!(textCreator instanceof PuzzleModeTextCreator)) {
			if (textCreator instanceof TreeLikeTextCreator && getDocument().getLength() > 0) {
				appendText(System.lineSeparator(), MAIN);
			}
			appendText(game.getResultStr(), MAIN);
		}

		// TN:
		// Unfortunately, there appears sometimes a line-break within the result. This could be
		// avoided by replacing the above line by the following line. But this looks more often
		// weird, too. So, a perfect solution would be to decide, when the new line is 
		// necessary. Can this be done somehow? It should work, if the component is resized, too. 
		//		appendText(System.lineSeparator() + game.getResultStr(), MAIN);

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
		// Looks like JEditorPane, but does not support wrap-style word.
	}

	// ======================================================================
	// Methods to walk through the game

	private boolean goBackward() {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			return game.goBack();
		}
		return false;
	}

	private void goBackToLineBegin() {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.goBackToLineBegin();
		}
	}

	private void gotoEndOfLine() {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.gotoEndOfLine();
		}
	}

	private boolean goForward() {
		if (userAction != UserAction.ENABLED && userAction != UserAction.NAVIGABLE) {
			return false;
		}
		int num = game.getNumOfNextMoves();
		boolean retVal = false;
		if (num > 1) {
			retVal = game.goForward(0);
		} else if (num == 1) {
			retVal = game.goForward();
		}
		return retVal;
	}

	private void gotoStart() {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.gotoStart();
		}
	}

	private void gotoEnd() {
		if (userAction == UserAction.ENABLED || userAction == UserAction.NAVIGABLE) {
			game.gotoEnd();
		}
	}

	private int getNodeForCaret() {
		int caret = getCaretPosition();
		if (caret < 3) {
			return game.getRootNode();
		}
		for (int i = 0; i < moveNode.length - 1; i++) {
			if (moveNrBegin[i + 1] > caret) {
				return moveNode[i];
			}
		}
		if (moveNode.length == 0) {
			return game.getRootNode();
		} else {
			// Here we need the last node. The old 
			// return moveNode[moveNode.length - 1];
			// does not work in puzzle mode, because moveNode is not completely filled until all moves are shown.
			int j = moveNode.length - 1;
			while (moveNode[j] == 0 && j > 0) {
				--j;
			}
			return moveNode[j];
		}
	}

	private void gotoPlyForCaret() {
		int newNode = getNodeForCaret();
		if (game.getCurNode() != newNode) {
			game.gotoNode(newNode);
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
