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
package chesspresso.game;

import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.position.FEN;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.Position;
import chesspresso.position.PositionChangeListener;

/**
 * Abstraction of a chess game.
 *
 * A chess game consists of the following parts:
 * <ul>
 * <li>{@link GameHeaderModel} containing information about the game header, for
 * instance white name, event, site
 * <li>{@link GameMoveModel} containing the moves, lines, comments of the game.
 * <li>a cursor and the current position in the game.
 * </ul>
 *
 * If you only need the information, not a cursor, use {@link GameModel}
 * consisting of {@link GameHeaderModel} and {@link GameMoveModel}.
 *
 * The game offers the following groups of operation:
 * <ul>
 * <li>direct access to values of the game header
 * <li>methods to append or delete lines of the move model
 * <li>methods to handle listeners for game changes
 * <li>methods to walk through the game, beginning with <code>go</code>
 * <li>a method to {@link #traverse(TraverseListener, boolean)} the game in
 * postfix order (the order used by {@link chesspresso.pgn.PGN})
 * </ul>
 *
 * @author Bernhard Seybold
 * 
 */
public class Game implements PositionChangeListener, Serializable {
    private static final long serialVersionUID = 1L;

    private static boolean DEBUG = false;

    // ======================================================================

    // finals, because of the dependence of the three variables!
    private final GameModel m_model;
    private final GameHeaderModel m_header;
    private final GameMoveModel m_moves;
    private Position m_position;
    private int m_cur;
    private boolean m_ignoreNotifications;
    private boolean m_alwaysAddLine; // during pgn parsing, always add new lines
    private List<GameModelChangeListener> m_changeListeners;

    // ======================================================================

    public Game() {
	this(new GameModel());
    }

    public Game(GameModel gameModel) {
	m_model = gameModel;
	m_header = gameModel.getHeaderModel();
	m_moves = gameModel.getMoveModel();

	String fen = m_header.getTag(PGN.TAG_FEN);
	if (fen != null) {
	    setPosition(new Position(fen, false));
	} else {
	    setPosition(Position.createInitialPosition());
	}
	m_ignoreNotifications = false;
	m_alwaysAddLine = false;
    }

    public Game getDeepCopy() {
	return new Game(this.m_model.getDeepCopy());
    }

    /*
     * Returns a game fragment starting with the given new ply offset and having a
     * given number of plies. If argument numOfPlies < 0, then all remaining moves
     * are added. If newPlyOffset < Position::getPlyOffset or newPlyOffset >
     * Position::getPlyOffset + Game::getNumOfPlies, then null is returned. If
     * numOfPlies >= 0 and newPlyOffset + numOfPlies > getPlyOffset() +
     * getNumOfPlies(), then a fragment with less plies is returned.
     */
    public Game getFragment(int newPlyOffset, int numOfPlies) {
	if (newPlyOffset < m_position.getPlyOffset() || newPlyOffset > m_position.getPlyOffset() + getNumOfPlies()) {
	    return null;
	}
	if (numOfPlies < 0) { // all moves starting with ply startPly
	    numOfPlies = 32676;
	}
	Game copy = getDeepCopy();
	copy.gotoPly(newPlyOffset);
	Game fragment = new Game(new GameModel(copy.m_header.getDeepCopy(), new GameMoveModel()));
	String fen = copy.getPosition().getFEN();
	if (!fen.equals(FEN.START_POSITION)) {
	    fragment.setTag(PGN.TAG_FEN, fen);
	}
	FEN.initFromFEN(fragment.m_position, fen);
	fragment.m_position.setPlyOffset(newPlyOffset);
	while (copy.goForward() && numOfPlies > 0) {
	    Move move = copy.getLastMove();
	    try {
		fragment.m_position.doMove(move);
	    } catch (IllegalMoveException ignore) {
		return null;
	    }
	    fragment.setPreMoveComment(copy.getPreMoveComment());
	    fragment.setPostMoveComment(copy.getPostMoveComment());
	    short[] nags = copy.getNags();
	    if (nags != null) {
		for (short nag : nags) {
		    fragment.addNag(nag);
		}
	    }
	    --numOfPlies;
	}

	return fragment;
    }

    // ======================================================================

    public GameModel getModel() {
	return m_model;
    }

    public Position getPosition() {
	return m_position;
    }

    public int getCurNode() {
	return m_cur;
    }

    public int getRootNode() {
	return 0;
    }

    public void pack() {
	m_cur = m_moves.pack(m_cur);
    }

    private void setPosition(Position position) {
	Variant oldVariant = null;
	if (m_position != null) {
	    oldVariant = m_position.getVariant();
	    List<PositionChangeListener> listeners = m_position.getPositionChangeListeners();
	    m_position = position;
	    for (PositionChangeListener listener : listeners) {
		m_position.addPositionChangeListener(listener);
	    }
	} else { // old code:
	    m_position = position;
	    m_position.addPositionChangeListener(this);
	}
	m_cur = 0;
	if (m_position.getVariant() == Variant.CHESS960) {
	    setVariant(m_position.getVariant());
	}
	if (oldVariant != null && oldVariant == Variant.CHESS960) {
	    m_position.setVariant(oldVariant);
	}
	m_position.firePositionChanged();
    }

    public void setAlwaysAddLine(boolean alwaysAddLine) {
	m_alwaysAddLine = alwaysAddLine;
    }

    // ======================================================================

    public void addChangeListener(GameModelChangeListener listener) {
	if (m_changeListeners == null)
	    m_changeListeners = new ArrayList<GameModelChangeListener>();
	m_changeListeners.add(listener);
    }

    public void removeChangeListener(GameModelChangeListener listener) {
	m_changeListeners.remove(listener);
	if (m_changeListeners.size() == 0)
	    m_changeListeners = null;
    }

    protected void fireMoveModelChanged() {
	if (m_changeListeners != null) {
	    for (GameModelChangeListener m_changeListener : m_changeListeners) {
		m_changeListener.moveModelChanged(this);
	    }
	}
    }

    protected void fireHeaderModelChanged() {
	if (m_changeListeners != null) {
	    for (GameModelChangeListener m_changeListener : m_changeListeners) {
		m_changeListener.headerModelChanged(this);
	    }
	}
    }

    // ======================================================================
    // methods of PositionChangeListener

    @Override
    public void notifyPositionChanged(ImmutablePosition position) {
    }

    @Override
    public void notifyMoveDone(ImmutablePosition position, short move) {
	if (DEBUG)
	    System.out.println("ChGame: move made in position " + move);

	if (!m_ignoreNotifications) {
	    if (!m_alwaysAddLine) {
		short[] moves = getNextShortMoves();
		for (int i = 0; i < moves.length; i++) {
		    if (moves[i] == move) {
			m_cur = m_moves.goForward(m_cur, i);
			return; // =====>
		    }
		}
	    }
	    m_cur = m_moves.appendAsRightMostLine(m_cur, move);
	    fireMoveModelChanged();
	}
    }

    @Override
    public void notifyMoveUndone(ImmutablePosition position) {
	if (DEBUG)
	    System.out.println("ChGame: move taken back in position");

	if (!m_ignoreNotifications) {
	    m_cur = m_moves.goBack(m_cur, true);
	}
    }

    // ======================================================================
    // header methods

    public String getTag(String tagName) {
	return m_header.getTag(tagName);
    }

    public String[] getTags() {
	return m_header.getTags();
    }

    public String[] getOtherTags() {
	return m_header.getOtherTags();
    }

    public void setTag(String tagName, String tagValue) {
	m_header.setTag(tagName, tagValue);
	fireHeaderModelChanged();
    }

    public void setGameByFEN(String fen, boolean overwriteTags) throws IllegalArgumentException {
	if (overwriteTags) {
	    m_header.clearTags();
	    m_header.setTag(PGN.TAG_DATE, "????.??.??");
	    m_header.setTag(PGN.TAG_ROUND, "?");
	    m_header.setTag(PGN.TAG_ECO, "");
	    m_header.setTag(PGN.TAG_RESULT, "*");
	}
	m_header.setTag(PGN.TAG_FEN, fen);
	setPosition(new Position(fen, false));
	m_moves.clear();
	setVariant(FEN.isShredderFEN(fen) ? Variant.CHESS960 : Variant.STANDARD);
	fireMoveModelChanged();
	fireHeaderModelChanged();
    }

    public String getEvent() {
	return m_header.getEvent();
    }

    public String getSite() {
	return m_header.getSite();
    }

    public String getDate() {
	return m_header.getDate();
    }

    public String getRound() {
	return m_header.getRound();
    }

    public String getWhite() {
	return m_header.getWhite();
    }

    public String getBlack() {
	return m_header.getBlack();
    }

    public String getResultStr() {
	return m_header.getResultStr();
    }

    public String getWhiteEloStr() {
	return m_header.getWhiteEloStr();
    }

    public String getBlackEloStr() {
	return m_header.getBlackEloStr();
    }

    public String getEventDate() {
	return m_header.getEventDate();
    }

    public String getECO() {
	return m_header.getECO();
    }

    public int getResult() {
	return m_header.getResult();
    }

    public int getWhiteElo() {
	return m_header.getWhiteElo();
    }

    public int getBlackElo() {
	return m_header.getBlackElo();
    }

    public String getDateWithoutQuestionMarks() {
	String s = m_header.getDate();
	if (s.startsWith("?")) {
	    return "";
	}
	if (s.contains(".?")) {
	    s = s.substring(0, s.indexOf(".?"));
	}
	return s;
    }

    public String getRoundWithoutQuestionmark() {
	if (m_header.getRound() == null || m_header.getRound().equals("?")) {
	    return "";
	}
	return m_header.getRound();
    }

    public String getSingleLineDescription() {
	String date = getDate();
	if ((date != null) && date.indexOf("?") >= 1) {
	    date = date.substring(0, date.indexOf("?") - 1);
	}
	String s = getWhite() + " vs " + getBlack();
	if ((getEvent() != null) && !getEvent().isEmpty()) {
	    s += ", " + getEvent();
	}
	if ((getSite() != null) && !getSite().isEmpty()) {
	    s += ", " + getSite();
	}
	s += ", " + date;
	return s;
    }

    public Variant getVariant() {
	return m_header.getVariant();
    }

    public void setVariant(Variant variant) {
	m_header.setVariant(variant);
	m_position.setVariant(variant);
    }

    // ======================================================================

    /**
     * Returns whether the given position occurs in the main line of this game.
     *
     * @param position the position to look for, must not be null
     * @return whether the given position occurs in the main line of this game
     */
    public boolean containsPosition(ImmutablePosition position) {
	boolean res = false;
	int index = getCurNode();
	gotoStart(true);
	for (;;) {
	    if (m_position.getHashCode() == position.getHashCode()) {
		res = true;
		break;
	    }
	    if (!hasNextMove())
		break;
	    goForward(true);
	}
	gotoNode(index, true);
	return res;
    }

    // ======================================================================

    /**
     * Returns info about the game consisting of white player, black player and
     * result.
     *
     * @return the info string
     */
    public String getInfoString() {
	return getWhite() + " - " + getBlack() + " " + getResultStr();
    }

    /**
     * Returns info about the game consisting of white player, black player, event,
     * site, date, result, and ECO.
     *
     * @return the info string
     */
    public String getLongInfoString() {
	StringBuffer sb = new StringBuffer();
	sb.append(getWhite()).append(" - ").append(getBlack()).append(", ").append(getEvent());
	if (getRound() != null && !getRound().equals("?")) {
	    sb.append(" (").append(getRound()).append(") ");
	}
	sb.append(", ").append(getSite()).append(" ").append(getResultStr());
	String date = getDateWithoutQuestionMarks();
	if (date != null) {
	    sb.append(", ").append(date);
	}
	if (getECO() != null) {
	    sb.append("  [").append(getECO()).append("]");
	}
	int numOfMoves = (getNumOfPlies() + 1) / 2;
	if (numOfMoves == 1) {
	    sb.append(" (1 move)");
	} else {
	    sb.append(" (").append(numOfMoves).append(" moves)");
	}
	return sb.toString();
    }

    /**
     * Returns information to display at the header of a game. The information is
     * split in three parts: (1) white and black player plus their elos, (2) event,
     * site, date, round, and (3) the ECO.
     *
     * @param line which line to return (0..2)
     * @return the info string
     */
    public String getHeaderString(int line) {
	if (line == 0) {
	    StringBuffer sb = new StringBuffer();
	    if (getWhite() == null) { // an empty game
		return sb.toString();
	    }
	    sb.append(getWhite());
	    if (getWhiteElo() != 0)
		sb.append(" [").append(getWhiteElo()).append("]");
	    sb.append(" - ").append(getBlack());
	    if (getBlackElo() != 0)
		sb.append(" [").append(getBlackElo()).append("]");
	    sb.append("  ").append(getResultStr()).append("  (").append(getNumOfMoves()).append(" moves)");
	    return sb.toString();
	} else if (line == 1) {
	    StringBuffer sb = new StringBuffer();
	    if (getEvent() == null) { // an empty game
		return sb.toString();
	    }
	    if (!getEvent().isEmpty()) {
		sb.append(getEvent()).append(", ");
	    }
	    if (getSite() != null && // PGN without site tag (has happened)
		    !getSite().isEmpty()) {
		sb.append(getSite()).append(", ");
	    }
	    sb.append(getDateWithoutQuestionMarks());
	    if (!getRoundWithoutQuestionmark().isEmpty()) {
		sb.append("  [").append(getRoundWithoutQuestionmark()).append("]");
	    }
	    if (getECO() != null) {
		sb.append(", ").append(getECO());
	    }
	    return sb.toString();
	} else {
	    throw new RuntimeException("Only 2 header lines supported");
	}
    }

    // ======================================================================
    // moves methods

    public boolean currentMoveHasNag(short nag) {
	return m_moves.hasNag(m_cur, nag);
    }

    public short[] getNags() {
	return m_moves.getNags(m_cur);
    }

    public void addNag(short nag) {
	m_moves.addNag(m_cur, nag);
	fireMoveModelChanged();
    }

    public void removeNag(short nag) {
	if (m_moves.removeNag(m_cur, nag))
	    fireMoveModelChanged();
    }

    public void removeAllNags() {
	if (m_moves.removeAllNags()) {
	    fireMoveModelChanged();
	}
    }

    public void stripAll() {
	gotoStart();
	removeAllComments();
	deleteAllLines();
	removeAllNags();
	fireMoveModelChanged();
    }

    // Comments:
    public boolean hasComment() {
	return m_moves.hasComment();
    }

    public String getPreMoveComment() {
	return m_moves.getPreMoveComment(m_cur);
    }

    public String getPostMoveComment() {
	return m_moves.getPostMoveComment(m_cur);
    }

    public void setPreMoveComment(String comment) {
	if (m_moves.setPreMoveComment(m_cur, comment)) {
	    while (Move.isSpecial(m_moves.getMove(m_cur))) {
		++m_cur;
	    }
	}
	fireMoveModelChanged();
    }

    public void setPostMoveComment(String comment) {
	m_moves.setPostMoveComment(m_cur, comment);
	fireMoveModelChanged();
    }

    public void removePreMoveComment() {
	String preComment = getPreMoveComment();
	if (preComment != null) {
	    if (m_moves.removePreMoveComment(m_cur)) {
		// this removePreMoveComment does not change m_cur because only the comment is
		// overwritten
		fireMoveModelChanged();
	    }
	}
    }

    public void removePostMoveComment() {
	if (m_moves.removePostMoveComment(m_cur))
	    fireMoveModelChanged();
    }

    // TODO: Could be done by means of traverse.
    public void removeAllComments() {
	int index = m_cur;
	gotoStart(true);
	removeAllComments(0); // here fireMoveModelChanged is called, if necessary
	if (index != 0) {
	    gotoNode(index, true); // index is indeed correct!
	} else {
	    gotoNode(index, false); // Workaround: here silent=true would not update the GameTextViewer
	}
    }

    private void removeAllComments(int level) {
	while (hasNextMove()) {
	    int numOfNextMoves = getNumOfNextMoves();
	    goForwardAndGetMove(true);
	    removePreMoveComment();
	    removePostMoveComment();
	    if (numOfNextMoves > 1) {
		for (int i = 1; i < numOfNextMoves; i++) {
		    goBack(true);
		    goForwardAndGetMove(i, true);
		    removePreMoveComment();
		    removePostMoveComment();
		    removeAllComments(level + 1);
		    goBackToMainLine(true);
		}
	    }
	}
    }

    public String getEmptyGameComment() {
	return m_moves.getEmptyGameComment();
    }

    public void setEmptyGameComment(String comment) {
	m_moves.setEmptyGameComment(comment.trim());
	fireMoveModelChanged();
    }

    // ======================================================================

    public int getCurrentPly() {
	return m_position.getPlyNumber();
    }

    public int getCurrentMoveNumber() {
	return (m_position.getPlyNumber() + 1) / 2;
    }

    public int getNextMoveNumber() {
	return (m_position.getPlyNumber() + 2) / 2;
    }

    public int getPlyOffset() {
	return m_position.getPlyOffset();
    }

    public int getNumOfPlies() {
	int num = 0;
	int index = 0;
	while (m_moves.hasNextMove(index)) {
	    index = m_moves.goForward(index);
	    num++;
	}
	return num;
    }

    public int getNumOfMoves() {
	return Chess.plyToMoveNumber(getNumOfPlies() - 1);
    }

    public int getTotalNumOfPlies() {
	return m_moves.getTotalNumOfPlies();
    }

    // ======================================================================

    public Move getLastMove() {
	return m_position.getLastMove();
    }

    public String getLastMoveAsSanWithNumber() {
	return m_position.getLastMoveAsSanWithNumber();
    }

    public Move getNextMove() {
	return getNextMove(0);
    }

    public short getNextShortMove() {
	return getNextShortMove(0);
    }

    public Move getNextMove(int whichLine) {
	short shortMove = m_moves.getMove(m_moves.goForward(m_cur, whichLine));
	if (shortMove == GameMoveModel.NO_MOVE)
	    return null;
	try {
	    m_position.setNotifyListeners(false);
	    m_position.doMove(shortMove);
	    Move move = m_position.getLastMove();
	    m_position.undoMove();
	    m_position.setNotifyListeners(true);
	    return move;
	} catch (IllegalMoveException ex) {
	    ex.printStackTrace();
	    return null;
	}
    }

    public short getNextShortMove(int whichLine) {
	return m_moves.getMove(m_moves.goForward(m_cur, whichLine));
    }

    public boolean hasNextMove() {
	return m_moves.hasNextMove(m_cur);
    }

    public int getNumOfNextMoves() {
	return m_moves.getNumOfNextMoves(m_cur);
    }

    public short[] getNextShortMoves() {
	short[] moves = new short[m_moves.getNumOfNextMoves(m_cur)];
	for (int i = 0; i < moves.length; i++) {
	    moves[i] = m_moves.getMove(m_moves.goForward(m_cur, i));
	}
	return moves;
    }

    public Move[] getNextMoves() {
	m_position.setNotifyListeners(false);
	Move[] moves = new Move[m_moves.getNumOfNextMoves(m_cur)];
	for (int i = 0; i < moves.length; i++) {
	    short move = m_moves.getMove(m_moves.goForward(m_cur, i));
	    try {
		m_position.doMove(move);
		// moves[i] = m_position.getLastMove(move);
		moves[i] = m_position.getLastMove();
		m_position.undoMove();
	    } catch (IllegalMoveException ex) {
		m_moves.write(System.out);
		System.out.println("cur = " + m_cur + " move=" + GameMoveModel.valueToString(move));
		ex.printStackTrace();
	    }
	}
	m_position.setNotifyListeners(true);
	return moves;
    }

    public boolean isMainLine() {
	return m_moves.isMainLine(m_cur);
    }

    // ======================================================================

    public boolean goBack() {
	return goBack(false);
    }

    public boolean goForward() {
	return goForward(false);
    }

    public boolean goForward(int whichLine) {
	return goForward(whichLine, false);
    }

    public void gotoStart() {
	gotoStart(false);
    }

    public void gotoEndOfLine() {
	gotoEndOfLine(false);
    }

    public void goBackToMainLine() {
	goBackToMainLine(false);
    }

    public void goBackToLineBegin() {
	goBackToLineBegin(false);
    }

    public void gotoNode(int node) {
	gotoNode(node, false);
    }

    public void gotoPosition(ImmutablePosition pos) {
	gotoPosition(pos, false);
    }

    public void deleteCurrentLine() {
	deleteCurrentLine(false);
    }

    public void deleteAllLines() {
	deleteAllLines(false);
    }

    public void deleteRemainingMoves() {
	deleteRemainingMoves(false);
    }

    public void gotoPly(int ply) {
	gotoStart();
	for (int i = 0; i < ply - getPlyOffset(); ++i) {
	    goForward();
	}
	// for a while we check the value
	if (ply < getPlyOffset() || ply > getPlyOffset() + getNumOfPlies()) {
	    System.err.println("Suspicious value in Game::gotoPly: ");
	    System.err
		    .println("   Ply: " + ply + ", plyOffset: " + getPlyOffset() + ", numOfPlies: " + getNumOfPlies());
	}
    }

    // ======================================================================

    private boolean goBack(boolean silent) {
	if (DEBUG)
	    System.out.println("goBack");

	int index = m_moves.goBack(m_cur, true);
	if (index != -1) {
	    m_cur = index;
	    m_ignoreNotifications = true;
	    if (silent)
		m_position.setNotifyListeners(false);
	    m_position.undoMove();
	    if (silent)
		m_position.setNotifyListeners(true);
	    m_ignoreNotifications = false;
	    return true;
	} else {
	    return false;
	}
    }

    private boolean goBackInLine(boolean silent) {
	if (DEBUG)
	    System.out.println("goBackInLine");

	int index = m_moves.goBack(m_cur, false);
	if (index != -1) {
	    m_cur = index; // needs to be set before undoing the move to allow
			   // listeners to check for curNode
	    m_ignoreNotifications = true;
	    if (silent)
		m_position.setNotifyListeners(false);
	    m_position.undoMove();
	    if (silent)
		m_position.setNotifyListeners(true);
	    m_ignoreNotifications = false;
	    return true;
	} else {
	    return false;
	}
    }

    private boolean goForward(boolean silent) {
	if (DEBUG)
	    System.out.println("goForward");

	return goForward(0, silent);
    }

    private Move goForwardAndGetMove(boolean silent) {
	if (DEBUG)
	    System.out.println("goForwardAndGetMove");

	return goForwardAndGetMove(0, silent);
    }

    private boolean goForward(int whichLine, boolean silent) {
	if (DEBUG)
	    System.out.println("goForward " + whichLine);

	int index = m_moves.goForward(m_cur, whichLine);
	short shortMove = m_moves.getMove(index);
	if (DEBUG)
	    System.out.println("  move = " + Move.getString(shortMove));
	if (shortMove != GameMoveModel.NO_MOVE) {
	    try {
		m_cur = index;
		m_ignoreNotifications = true;
		if (silent)
		    m_position.setNotifyListeners(false);
		m_position.doMove(shortMove);
		if (silent)
		    m_position.setNotifyListeners(true);
		m_ignoreNotifications = false;
		return true;
	    } catch (IllegalMoveException ex) {
		System.out.println(m_model.toString() + " at move " + Move.getString(shortMove));
		ex.printStackTrace();
	    }
	}
	return false;
    }

    private Move goForwardAndGetMove(int whichLine, boolean silent) {
	if (DEBUG)
	    System.out.println("goForwardAndGetMove " + whichLine);

	int index = m_moves.goForward(m_cur, whichLine);
	short shortMove = m_moves.getMove(index);
	if (DEBUG)
	    System.out.println("  move = " + Move.getString(shortMove));
	if (shortMove != GameMoveModel.NO_MOVE) {
	    try {
		m_cur = index;
		m_ignoreNotifications = true;
		if (silent)
		    m_position.setNotifyListeners(false);
		m_position.doMove(shortMove);
		Move move = m_position.getLastMove();
		if (silent)
		    m_position.setNotifyListeners(true);
		m_ignoreNotifications = false;
		return move;
	    } catch (IllegalMoveException ex) {
		ex.printStackTrace();
	    }
	}
	return null;
    }

    private void gotoStart(boolean silent) {
	while (goBack(silent))
	    ;
    }

    private void gotoEndOfLine(boolean silent) {
	while (goForward(silent))
	    ;
    }

    private void goBackToLineBegin(boolean silent) {
	if (DEBUG)
	    System.out.println("goBackToLineBegin");

	while (goBackInLine(silent))
	    ;
    }

    private void goBackToMainLine(boolean silent) {
	if (DEBUG)
	    System.out.println("goBackToMainLine");

	goBackToLineBegin(silent);
	goBack(silent);
	goForward(silent);
    }

    private int getNumOfPliesToRoot(int node) {
	int plies = 0;
	while (node > 0) {
	    node = m_moves.goBack(node, true);
	    plies++;
	}
	return plies;
    }

    private int[] getNodesToRoot(int node) {
	int[] nodes;
	int i = 0;
	if (m_moves.getMove(node) != GameMoveModel.NO_MOVE) {
	    nodes = new int[getNumOfPliesToRoot(node) + 1];
	    nodes[0] = node;
	    i = 1;
	} else {
	    nodes = new int[getNumOfPliesToRoot(node)];
	    i = 0;
	}
	for (; i < nodes.length; i++) {
	    node = m_moves.goBack(node, true);
	    nodes[i] = node;
	}
	return nodes;
    }

    // Note: a node is not necessarily a move
    public void gotoNode(int node, boolean silent) {
	int[] nodeNodes = getNodesToRoot(node);

	gotoStart(silent);
	for (int i = nodeNodes.length - 2; i >= 0; i--) {
	    int nextMoveIndex = 0;
	    for (int j = 1; j < getNumOfNextMoves(); j++) {
		if (m_moves.goForward(m_cur, j) == nodeNodes[i]) {
		    nextMoveIndex = j;
		    break;
		}
	    }
	    goForward(nextMoveIndex, silent);
	}
	m_cur = node; // now that we have made all the moves, set cur to node
    }

    public void gotoPosition(ImmutablePosition pos, boolean silent) {
	if (m_position.equals(pos))
	    return; // =====>

	int curNode = getCurNode();
	gotoStart(true);
	do {
	    if (m_position.equals(pos)) {
		int posNode = getCurNode();
		gotoNode(curNode, true);
		gotoNode(posNode, silent);
		return; // =====>
	    }
	} while (goForward(true));
    }

    // ======================================================================

    public boolean promoteVariation() {
	if (isMainLine()) {
	    return true;
	}
	int val = m_moves.promoteVariation(m_cur);
	if (val != -1) {
	    m_cur = val;
	    fireMoveModelChanged();
	    return true;
	} else {
	    return false;
	}
    }

    // ======================================================================

    public void deleteCurrentLine(boolean silent) {
	goBackToLineBegin();
	int index = m_cur;
	goBack(silent);
	if (0 == index) { // otherwise we get an exception in fireMoveModelChanged below
	    m_moves.clear();
	} else {
	    m_moves.deleteCurrentLine(index);
	}
	fireMoveModelChanged();
    }

    // ======================================================================

    public void deleteAllLines(boolean silent) {
	int index = m_cur;
	gotoStart();
	if (m_moves.deleteAllLines()) {
	    fireMoveModelChanged();
	} else {
	    gotoNode(index, silent);
	}
    }

    // ======================================================================

    public void deleteRemainingMoves(boolean silent) {
	if (goForward()) {
	    int index = m_cur;
	    if (goBack(silent)) {
		m_moves.deleteRemainingMoves(index);
		fireMoveModelChanged();
	    }
	}
    }

    // ======================================================================

    /**
     * Method to traverse the game in postfix order (first the lines, then the main
     * line). This method is used by {@link chesspresso.pgn.PGN}.
     *
     * @param listener  the listener to receive event when arriving at nodes
     * @param withLines whether or not to include lines of the current main line.
     */
    public void traverse(TraverseListener listener, boolean withLines) {
	int index = getCurNode();
	gotoStart(true);
	traverse(listener, withLines, m_position.getPlyNumber(), 0);
	gotoNode(index, true);
    }

    private void traverse(TraverseListener listener, boolean withLines, int plyNumber, int level) {
	while (hasNextMove()) {
	    int numOfNextMoves = getNumOfNextMoves();

	    Move move = goForwardAndGetMove(true);
	    listener.notifyMove(move, getNags(), getPreMoveComment(), getPostMoveComment(), plyNumber, level);

	    if (withLines && numOfNextMoves > 1) {
		for (int i = 1; i < numOfNextMoves; i++) {
		    goBack(true);
		    listener.notifyLineStart(level);

		    move = goForwardAndGetMove(i, true);
		    listener.notifyMove(move, getNags(), getPreMoveComment(), getPostMoveComment(), plyNumber,
			    level + 1);

		    traverse(listener, withLines, plyNumber + 1, level + 1);

		    goBackToMainLine(true);
		    if (i > 0)
			listener.notifyLineEnd(level);
		}
	    }

	    plyNumber++;
	}
    }

    // ======================================================================

    public void save(DataOutput out, int headerMode, int movesMode) throws IOException {
	m_model.save(out, headerMode, movesMode);
    }

    // ======================================================================

    /**
     * Returns the hash code of the game, which is defined as the hash code of the
     * move model. That means two game are considered equal if they contain exactly
     * the same lines. The header does not matter.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
	return getModel().hashCode();
    }

    /**
     * Returns whether two games are equal. This is the case if they contain exactly
     * the same lines. The header does not matter.
     *
     * @return the hash code
     */
    @Override
    public boolean equals(Object obj) {
	if (obj == this)
	    return true; // =====>
	if (!(obj instanceof Game))
	    return false; // =====>
	Game game = (Game) obj;
	return game.getModel().equals(getModel());
    }

    // ======================================================================

    /**
     * Returns a string representation of the game. Implemented as the string
     * representation of the header plus the move model.
     *
     * @return a string representation of the game
     */
    @Override
    public String toString() {
	return m_model.toString();
    }

    // ======================================================================

    public void removeEvaluationNags() {
	if (m_moves.removeEvaluationNags(m_cur)) {
	    fireMoveModelChanged();
	}
    }

    public void removePunctuationNags() {
	if (m_moves.removePunctuationNags(m_cur)) {
	    fireMoveModelChanged();
	}
    }
}
