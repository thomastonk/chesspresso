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
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.pgn.PGN;
import chesspresso.pgn.PGNReader;
import chesspresso.pgn.PGNSyntaxError;
import chesspresso.pgn.PGNWriter;
import chesspresso.position.FEN;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.Position;
import chesspresso.position.PositionListener;

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
public class Game implements PositionListener, Serializable {
    private static final long serialVersionUID = 2L;

    private static final boolean DEBUG = false;

    // ======================================================================

    private final GameModel m_model;
    private Position m_position;
    private int m_cur;
    private boolean m_ignoreNotifications;
    private boolean m_alwaysAddLine; // during pgn parsing, always add new lines
    private List<GameModelChangeListener> m_changeListeners;

    // NOTE: Don't forget to check readObject and copyByReflection, when changing
    // fields!

    // ======================================================================

    public Game() {
	this(new GameModel());
    }

    public Game(GameModel gameModel) {
	m_model = gameModel;

	String fen = m_model.getHeaderModel().getTag(PGN.TAG_FEN);
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
	Game fragment = new Game(new GameModel(copy.m_model.getHeaderModel().getDeepCopy(), new GameMoveModel()));
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
	m_cur = m_model.getMoveModel().pack(m_cur);
    }

    private void setPosition(Position position) {
	Variant oldVariant = null;
	if (m_position != null) {
	    oldVariant = m_position.getVariant();
	    List<PositionListener> listeners = m_position.getPositionListeners();
	    m_position = position;
	    for (PositionListener listener : listeners) {
		m_position.addPositionListener(listener);
	    }
	} else { // old code:
	    m_position = position;
	    m_position.addPositionListener(this);
	}
	m_cur = 0;
	if (m_position.getVariant() == Variant.CHESS960) {
	    setVariant(m_position.getVariant());
	}
	if (oldVariant == Variant.CHESS960) {
	    m_position.setVariant(oldVariant);
	}
    }

    public void setAlwaysAddLine(boolean alwaysAddLine) {
	m_alwaysAddLine = alwaysAddLine;
    }

    // ======================================================================

    public void addChangeListener(GameModelChangeListener listener) {
	if (m_changeListeners == null)
	    m_changeListeners = new ArrayList<>();
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
    // method of PositionListener

    @Override
    public void positionChanged(ChangeType type, ImmutablePosition pos, short move) {
	if (type == ChangeType.MOVE_DONE) {
	    if (DEBUG)
		System.out.println("Game: move " + move + " made.");

	    if (!m_ignoreNotifications) {
		if (!m_alwaysAddLine) {
		    short[] moves = getNextShortMoves();
		    for (int i = 0; i < moves.length; i++) {
			if (moves[i] == move) {
			    m_cur = m_model.getMoveModel().goForward(m_cur, i);
			    return; // =====>
			}
		    }
		}
		m_cur = m_model.getMoveModel().appendAsRightMostLine(m_cur, move);
		fireMoveModelChanged();
	    }
	} else if (type == ChangeType.MOVE_UNDONE) {
	    if (DEBUG)
		System.out.println("Game: move undone.");

	    if (!m_ignoreNotifications) {
		m_cur = m_model.getMoveModel().goBack(m_cur, true);
	    }
	}
    }

    // ======================================================================
    // header methods

    public String getTag(String tagName) {
	return m_model.getHeaderModel().getTag(tagName);
    }

    public String[] getTags() {
	return m_model.getHeaderModel().getTags();
    }

    public String[] getOtherTags() {
	return m_model.getHeaderModel().getOtherTags();
    }

    public void setTag(String tagName, String tagValue) {
	m_model.getHeaderModel().setTag(tagName, tagValue);
	fireHeaderModelChanged();
    }

    public void setGameByFEN(String fen, boolean overwriteTags) throws IllegalArgumentException {
	if (overwriteTags) {
	    m_model.getHeaderModel().clearTags();
	    m_model.getHeaderModel().setTag(PGN.TAG_DATE, "????.??.??");
	    m_model.getHeaderModel().setTag(PGN.TAG_ROUND, "?");
	    m_model.getHeaderModel().setTag(PGN.TAG_ECO, "");
	    m_model.getHeaderModel().setTag(PGN.TAG_RESULT, "*");
	}
	m_model.getHeaderModel().setTag(PGN.TAG_FEN, fen);
	setPosition(new Position(fen, false));
	m_model.getMoveModel().clear();
	setVariant(FEN.isShredderFEN(fen) ? Variant.CHESS960 : Variant.STANDARD);
	fireMoveModelChanged();
	fireHeaderModelChanged();
    }

    public String getEvent() {
	return m_model.getHeaderModel().getEvent();
    }

    public String getSite() {
	return m_model.getHeaderModel().getSite();
    }

    public String getDate() {
	return m_model.getHeaderModel().getDate();
    }

    public String getRound() {
	return m_model.getHeaderModel().getRound();
    }

    public String getWhite() {
	return m_model.getHeaderModel().getWhite();
    }

    public String getBlack() {
	return m_model.getHeaderModel().getBlack();
    }

    public String getResultStr() {
	return m_model.getHeaderModel().getResultStr();
    }

    public String getWhiteEloStr() {
	return m_model.getHeaderModel().getWhiteEloStr();
    }

    public String getBlackEloStr() {
	return m_model.getHeaderModel().getBlackEloStr();
    }

    public String getEventDate() {
	return m_model.getHeaderModel().getEventDate();
    }

    public String getECO() {
	return m_model.getHeaderModel().getECO();
    }

    public int getResult() {
	return m_model.getHeaderModel().getResult();
    }

    public int getWhiteElo() {
	return m_model.getHeaderModel().getWhiteElo();
    }

    public int getBlackElo() {
	return m_model.getHeaderModel().getBlackElo();
    }

    public String getDateWithoutQuestionMarks() {
	String s = m_model.getHeaderModel().getDate();
	if (s.startsWith("?")) {
	    return "";
	}
	if (s.contains(".?")) {
	    s = s.substring(0, s.indexOf(".?"));
	}
	return s;
    }

    public String getRoundWithoutQuestionmark() {
	if (m_model.getHeaderModel().getRound() == null || m_model.getHeaderModel().getRound().equals("?")) {
	    return "";
	}
	return m_model.getHeaderModel().getRound();
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
	return m_model.getHeaderModel().getVariant();
    }

    public void setVariant(Variant variant) {
	m_model.getHeaderModel().setVariant(variant);
	m_position.setVariant(variant);
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
	StringBuilder sb = new StringBuilder();
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
	    StringBuilder sb = new StringBuilder();
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
	    StringBuilder sb = new StringBuilder();
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
	return m_model.getMoveModel().hasNag(m_cur, nag);
    }

    public short[] getNags() {
	return m_model.getMoveModel().getNags(m_cur);
    }

    public void addNag(short nag) {
	m_model.getMoveModel().addNag(m_cur, nag);
	fireMoveModelChanged();
    }

    public void removeNag(short nag) {
	if (m_model.getMoveModel().removeNag(m_cur, nag))
	    fireMoveModelChanged();
    }

    public void removeAllNags() {
	if (m_model.getMoveModel().removeAllNags()) {
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
	return m_model.getMoveModel().hasComment();
    }

    public String getPreMoveComment() {
	return m_model.getMoveModel().getPreMoveComment(m_cur);
    }

    public String getPostMoveComment() {
	return m_model.getMoveModel().getPostMoveComment(m_cur);
    }

    public void setPreMoveComment(String comment) {
	if (m_model.getMoveModel().setPreMoveComment(m_cur, comment)) {
	    while (Move.isSpecial(m_model.getMoveModel().getMove(m_cur))) {
		++m_cur;
	    }
	}
	fireMoveModelChanged();
    }

    public void setPostMoveComment(String comment) {
	m_model.getMoveModel().setPostMoveComment(m_cur, comment);
	fireMoveModelChanged();
    }

    public void removePreMoveComment() {
	removePreMoveComment(false);
    }

    private void removePreMoveComment(boolean silent) {
	String preComment = getPreMoveComment();
	if (preComment != null) {
	    if (m_model.getMoveModel().removePreMoveComment(m_cur)) {
		// this removePreMoveComment does not change m_cur because only the comment is
		// overwritten
		if (!silent) {
		    fireMoveModelChanged();
		}
	    }
	}
    }

    public void removePostMoveComment() {
	removePostMoveComment(false);
    }

    private void removePostMoveComment(boolean silent) {
	if (m_model.getMoveModel().removePostMoveComment(m_cur) && !silent) {
	    fireMoveModelChanged();
	}
    }

    public void removeAllComments() {
	if (getNumOfPlies() == 0) {
	    m_model.getMoveModel().setEmptyGameComment(null);
	} else {
	    traverse(new TraverseListener() {
		@Override
		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment,
			int plyNumber, int level) {
		    removePreMoveComment(true);
		    removePostMoveComment(true);
		}

		@Override
		public void notifyLineStart(int level) {
		}

		@Override
		public void notifyLineEnd(int level) {
		}
	    }, true);
	}
	fireMoveModelChanged();
    }

    public String getEmptyGameComment() {
	return m_model.getMoveModel().getEmptyGameComment();
    }

    public void setEmptyGameComment(String comment) {
	m_model.getMoveModel().setEmptyGameComment(comment.trim());
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
	while (m_model.getMoveModel().hasNextMove(index)) {
	    index = m_model.getMoveModel().goForward(index);
	    num++;
	}
	return num;
    }

    public int getNumOfMoves() {
	return Chess.plyToMoveNumber(getNumOfPlies() - 1);
    }

    public int getTotalNumOfPlies() {
	return m_model.getMoveModel().getTotalNumOfPlies();
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
	short moveAsShort = m_model.getMoveModel().getMove(m_model.getMoveModel().goForward(m_cur, whichLine));
	if (moveAsShort == GameMoveModel.NO_MOVE)
	    return null;
	return m_position.getNextMove(moveAsShort);
    }

    public short getNextShortMove(int whichLine) {
	return m_model.getMoveModel().getMove(m_model.getMoveModel().goForward(m_cur, whichLine));
    }

    public boolean hasNextMove() {
	return m_model.getMoveModel().hasNextMove(m_cur);
    }

    public int getNumOfNextMoves() {
	return m_model.getMoveModel().getNumOfNextMoves(m_cur);
    }

    public short[] getNextShortMoves() {
	short[] moves = new short[m_model.getMoveModel().getNumOfNextMoves(m_cur)];
	for (int i = 0; i < moves.length; i++) {
	    moves[i] = m_model.getMoveModel().getMove(m_model.getMoveModel().goForward(m_cur, i));
	}
	return moves;
    }

    public Move[] getNextMoves() {
	Move[] moves = new Move[m_model.getMoveModel().getNumOfNextMoves(m_cur)];
	for (int i = 0; i < moves.length; i++) {
	    short moveAsShort = m_model.getMoveModel().getMove(m_model.getMoveModel().goForward(m_cur, i));
	    moves[i] = m_position.getNextMove(moveAsShort);
	}
	return moves;
    }

    public boolean isMainLine() {
	return m_model.getMoveModel().isMainLine(m_cur);
    }

    // ======================================================================

    public boolean goBack() {
	if (DEBUG)
	    System.out.println("goBack");

	int index = m_model.getMoveModel().goBack(m_cur, true);
	if (index != -1) {
	    m_cur = index;
	    m_ignoreNotifications = true;
	    m_position.undoMove();
	    m_ignoreNotifications = false;
	    return true;
	} else {
	    return false;
	}
    }

    public boolean goForward() {
	if (DEBUG)
	    System.out.println("goForward");

	return goForward(0);
    }

    public boolean goForward(int whichLine) {
	if (DEBUG)
	    System.out.println("goForward " + whichLine);

	int index = m_model.getMoveModel().goForward(m_cur, whichLine);
	short shortMove = m_model.getMoveModel().getMove(index);
	if (DEBUG)
	    System.out.println("  move = " + Move.getString(shortMove));
	if (shortMove != GameMoveModel.NO_MOVE) {
	    try {
		m_cur = index;
		m_ignoreNotifications = true;
		m_position.doMove(shortMove);
		m_ignoreNotifications = false;
		return true;
	    } catch (IllegalMoveException ex) {
		System.out.println(m_model.toString() + " at move " + Move.getString(shortMove));
		ex.printStackTrace();
	    }
	}
	return false;
    }

    public void gotoStart() {
	m_position.increaseAlgorithmDepth();
	while (goBack())
	    ;
	m_position.decreaseAlgorithmDepth();
    }

    public void gotoEndOfLine() {
	m_position.increaseAlgorithmDepth();
	while (goForward())
	    ;
	m_position.decreaseAlgorithmDepth();
    }

    public void goBackToParentLine() {
	if (DEBUG)
	    System.out.println("goBackToMainLine");

	m_position.increaseAlgorithmDepth();
	goBackToLineBegin();
	goBack();
	goForward();
	m_position.decreaseAlgorithmDepth();
    }

    public void goBackToLineBegin() {
	if (DEBUG)
	    System.out.println("goBackToLineBegin");

	m_position.increaseAlgorithmDepth();
	while (goBackInLine()) {
	    ;
	}
	m_position.decreaseAlgorithmDepth();

    }

    // Note: a node is not necessarily a move.
    public void gotoNode(int node) {
	int[] nodeNodes = getNodesToRoot(node);

	m_position.increaseAlgorithmDepth();
	gotoStart();
	for (int i = nodeNodes.length - 2; i >= 0; i--) {
	    int nextMoveIndex = 0;
	    for (int j = 1; j < getNumOfNextMoves(); j++) {
		if (m_model.getMoveModel().goForward(m_cur, j) == nodeNodes[i]) {
		    nextMoveIndex = j;
		    break;
		}
	    }
	    goForward(nextMoveIndex);
	}
	m_cur = node; // now that we have made all the moves, set cur to node
	m_position.decreaseAlgorithmDepth();
    }

    public void deleteCurrentLine() {
	m_position.increaseAlgorithmDepth();
	goBackToLineBegin();
	int index = m_cur;
	goBack();
	if (0 == index) { // otherwise we get an exception in fireMoveModelChanged below
	    m_model.getMoveModel().clear();
	} else {
	    m_model.getMoveModel().deleteCurrentLine(index);
	}
	fireMoveModelChanged();
	m_position.decreaseAlgorithmDepth();
    }

    public void deleteAllLines() {
	if (!isMainLine()) {
	    m_position.increaseAlgorithmDepth();
	    gotoStart();
	    m_position.decreaseAlgorithmDepth();
	}
	if (m_model.getMoveModel().deleteAllLines()) {
	    fireMoveModelChanged();
	}
    }

    public void deleteRemainingMoves() {
	if (m_model.getMoveModel().deleteRemainingMoves(m_cur)) {
	    fireMoveModelChanged();
	}
    }

    public void gotoPly(int ply) {
	if (getCurrentPly() == ply) {
	    return;
	}
	m_position.increaseAlgorithmDepth();
	gotoStart();
	for (int i = 0; i < ply - getPlyOffset(); ++i) {
	    goForward();
	}
	m_position.decreaseAlgorithmDepth();
	// for a while we check the value
	if (ply < getPlyOffset() || ply > getPlyOffset() + getNumOfPlies()) {
	    System.err.println("Suspicious value in Game::gotoPly: ");
	    System.err
		    .println("   Ply: " + ply + ", plyOffset: " + getPlyOffset() + ", numOfPlies: " + getNumOfPlies());
	}
    }

    // ======================================================================

    private boolean goBackInLine() {
	if (DEBUG)
	    System.out.println("goBackInLine");

	int index = m_model.getMoveModel().goBack(m_cur, false);
	if (index != -1) {
	    m_cur = index; // needs to be set before undoing the move to allow
			   // listeners to check for curNode
	    m_ignoreNotifications = true;
	    m_position.undoMove();
	    m_ignoreNotifications = false;
	    return true;
	} else {
	    return false;
	}
    }

    private Move goForwardAndGetMove() {
	if (DEBUG)
	    System.out.println("goForwardAndGetMove");

	return goForwardAndGetMove(0);
    }

    private Move goForwardAndGetMove(int whichLine) {
	if (DEBUG)
	    System.out.println("goForwardAndGetMove " + whichLine);

	int index = m_model.getMoveModel().goForward(m_cur, whichLine);
	short shortMove = m_model.getMoveModel().getMove(index);
	if (DEBUG)
	    System.out.println("  move = " + Move.getString(shortMove));
	if (shortMove != GameMoveModel.NO_MOVE) {
	    try {
		m_cur = index;
		m_ignoreNotifications = true;
		m_position.doMove(shortMove);
		Move move = m_position.getLastMove();
		m_ignoreNotifications = false;
		return move;
	    } catch (IllegalMoveException ex) {
		ex.printStackTrace();
	    }
	}
	return null;
    }

    private int getNumOfPliesToRoot(int node) {
	int plies = 0;
	while (node > 0) {
	    node = m_model.getMoveModel().goBack(node, true);
	    plies++;
	}
	return plies;
    }

    private int[] getNodesToRoot(int node) {
	int[] nodes;
	int i = 0;
	if (m_model.getMoveModel().getMove(node) != GameMoveModel.NO_MOVE) {
	    nodes = new int[getNumOfPliesToRoot(node) + 1];
	    nodes[0] = node;
	    i = 1;
	} else {
	    nodes = new int[getNumOfPliesToRoot(node)];
	    i = 0;
	}
	for (; i < nodes.length; i++) {
	    node = m_model.getMoveModel().goBack(node, true);
	    nodes[i] = node;
	}
	return nodes;
    }

    // ======================================================================

    public boolean promoteVariation() {
	if (isMainLine()) {
	    return true;
	}
	int val = m_model.getMoveModel().promoteVariation(m_cur);
	if (val != -1) {
	    m_cur = val;
	    fireMoveModelChanged();
	    return true;
	} else {
	    return false;
	}
    }

    // ======================================================================

    /**
     * Method to traverse the game in natural order, i.e. as it is needed in the
     * GameTextViewer.
     *
     * @param listener  the listener that receives events when arriving at nodes
     * @param withLines whether or not to include sub-lines of the main line.
     */
    public void traverse(TraverseListener listener, boolean withLines) {
	m_position.increaseAlgorithmDepth();
	int index = getCurNode();
	gotoStart();
	traverse(listener, withLines, m_position.getPlyNumber(), 0);
	gotoNode(index);
	m_position.decreaseAlgorithmDepth();
    }

    private void traverse(TraverseListener listener, boolean withLines, int plyNumber, int level) {
	while (hasNextMove()) {
	    int numOfNextMoves = getNumOfNextMoves();

	    Move move = goForwardAndGetMove();
	    listener.notifyMove(move, getNags(), getPreMoveComment(), getPostMoveComment(), plyNumber, level);

	    if (withLines && numOfNextMoves > 1) {
		for (int i = 1; i < numOfNextMoves; i++) {
		    goBack();
		    listener.notifyLineStart(level);

		    move = goForwardAndGetMove(i);
		    listener.notifyMove(move, getNags(), getPreMoveComment(), getPostMoveComment(), plyNumber,
			    level + 1);

		    traverse(listener, withLines, plyNumber + 1, level + 1);

		    goBackToParentLine();
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
	if (m_model.getMoveModel().removeEvaluationNags(m_cur)) {
	    fireMoveModelChanged();
	}
    }

    public void removePunctuationNags() {
	if (m_model.getMoveModel().removePunctuationNags(m_cur)) {
	    fireMoveModelChanged();
	}
    }

    // ======================================================================

    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
	String str = s.readUTF();
	StringReader strReader = new StringReader(str);
	PGNReader pgnRreader = new PGNReader(strReader, "no file");
	Game game;
	try {
	    game = pgnRreader.parseGame();
	} catch (PGNSyntaxError e) {
	    throw new IOException(e.getMessage());
	}
	copyByReflection(game);
    }

    private void copyByReflection(Game game) throws IOException {
	Class<? extends Game> c = this.getClass();
	Field[] fields = c.getDeclaredFields();
	for (Field f : fields) {
	    if (Modifier.isStatic(f.getModifiers())) { // ignore statics
		continue;
	    }
	    try {
		f.setAccessible(true); // for finals
		f.set(this, f.get(game));
	    } catch (IllegalArgumentException | IllegalAccessException e) {
		throw new IOException(e.getMessage());
	    }
	}
	m_position.removePositionListener(game);
	m_position.addPositionListener(this);
    }

    private synchronized void writeObject(java.io.ObjectOutputStream s) throws IOException {
	StringWriter buf = new StringWriter();
	PGNWriter writer = new PGNWriter(buf);
	writer.write(this);
	s.writeUTF(buf.toString());
    }
}
