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

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serial;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
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
import chesspresso.position.InvalidFenException;
import chesspresso.position.Position;

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
public non-sealed class Game implements Comparable<Game>, RelatedGame, Serializable {
	@Serial
	private static final long serialVersionUID = 2L;

	private static final boolean DEBUG = false;

	// ======================================================================

	private final GameModel model;
	private final Position position;
	private int cur;
	private boolean ignoreNotifications;
	private boolean alwaysAddLine; // during pgn parsing, always add new lines
	private List<GameModelChangeListener> changeListeners;

	// NOTE: Don't forget to check readObject, when changing fields!

	// ======================================================================

	public Game() {
		this(new GameModel());
	}

	public Game(Game game) {
		this(game.getModel());
		getPosition().setPlyOffset(game.getPlyOffset());
	}

	private Game(GameModel gameModel) {
		model = gameModel;
		position = new Position();
		position.addPositionListener(this);

		String fen = model.getHeaderModel().getTag(PGN.TAG_FEN);
		ignoreNotifications = true;
		if (fen != null) {
			try {
				position.setPositionSnapshot(new Position(fen, false));
			} catch (InvalidFenException _) {
				position.setPositionSnapshot(Position.createInitialPosition());
			}
		} else {
			position.setPositionSnapshot(Position.createInitialPosition());
		}
		ignoreNotifications = false;
		alwaysAddLine = false;
	}

	public Game getDeepCopy() {
		Game copy = new Game(this.model.getDeepCopy());
		copy.getPosition().setPlyOffset(getPlyOffset());
		return copy;
	}

	/*
	 * Returns a game fragment starting with the given new ply offset and having a
	 * given number of plies. If argument numOfPlies < 0, then all remaining moves
	 * are added. If newPlyOffset < Position::getPlyOffset or newPlyOffset >
	 * Position::getPlyOffset + Game::getNumOfPlies, then null is returned. If
	 * numOfPlies >= 0 and newPlyOffset + numOfPlies > getPlyOffset() +
	 * getNumOfPlies(), then a fragment with fewer plies is returned.
	 */
	public Game getFragment(int newPlyOffset, int numOfPlies) {
		if (newPlyOffset < position.getPlyOffset() || newPlyOffset > position.getPlyOffset() + getNumOfPlies()) {
			return null;
		}
		if (numOfPlies < 0) { // all moves starting with ply startPly
			numOfPlies = 32676;
		}
		Game copy = getDeepCopy();
		copy.gotoPly(newPlyOffset);
		Game fragment = new Game(new GameModel(copy.model.getHeaderModel().getDeepCopy(), new GameMoveModel()));
		String fen = copy.getPosition().getFEN();
		if (!fen.equals(FEN.START_POSITION)) {
			fragment.setTag(PGN.TAG_FEN, fen);
		}
		try {
			fragment.position.initFromFEN(fen, true);
		} catch (InvalidFenException _) {
		}
		fragment.position.setPlyOffset(newPlyOffset);
		while (copy.goForward() && numOfPlies > 0) {
			Move move = copy.getLastMove();
			try {
				fragment.position.doMove(move);
			} catch (IllegalMoveException _) {
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
		fragment.setTag(PGN.TAG_RESULT, copy.getResultStr());

		return fragment;
	}

	// ======================================================================

	private GameModel getModel() {
		return model;
	}

	public Position getPosition() {
		return position;
	}

	public int getCurNode() {
		return cur;
	}

	public int getRootNode() {
		return 0;
	}

	public void pack() {
		cur = model.getMoveModel().pack(cur);
	}

	public void setAlwaysAddLine(boolean alwaysAddLine) {
		this.alwaysAddLine = alwaysAddLine;
	}

	// ======================================================================

	public void addChangeListener(GameModelChangeListener listener) {
		if (changeListeners == null) {
			changeListeners = new ArrayList<>();
		}
		changeListeners.add(listener);
	}

	public void removeChangeListener(GameModelChangeListener listener) {
		if (changeListeners != null) {
			changeListeners.remove(listener);
			if (changeListeners.size() == 0) {
				changeListeners = null;
			}
		}
	}

	protected void fireMoveModelChanged() {
		if (changeListeners != null) {
			for (GameModelChangeListener changeListener : changeListeners) {
				changeListener.moveModelChanged(this);
			}
		}
	}

	protected void fireHeaderModelChanged() {
		if (changeListeners != null) {
			for (GameModelChangeListener changeListener : changeListeners) {
				changeListener.headerModelChanged(this);
			}
		}
	}

	// ======================================================================
	// header methods

	public String getTag(String tagName) {
		return model.getHeaderModel().getTag(tagName);
	}

	public String[] getTags() {
		return model.getHeaderModel().getTags();
	}

	public String[] getOtherTags() {
		return model.getHeaderModel().getOtherTags();
	}

	public void setTag(String tagName, String tagValue) {
		model.getHeaderModel().setTag(tagName, tagValue);
		fireHeaderModelChanged();
		if (PGN.TAG_RESULT.equals(tagName)) {
			fireMoveModelChanged();
		}
	}

	public void setGameByFEN(String fen, boolean overwriteTags) throws InvalidFenException {
		Position newPos = new Position(fen, false); // If this call throws, 'this' is unchanged!
		cur = 0;
		if (overwriteTags) {
			model.getHeaderModel().clearTags();
			model.getHeaderModel().setTag(PGN.TAG_DATE, "????.??.??");
			model.getHeaderModel().setTag(PGN.TAG_ROUND, "?");
			model.getHeaderModel().setTag(PGN.TAG_ECO, "");
			model.getHeaderModel().setTag(PGN.TAG_RESULT, "*");
		}
		ignoreNotifications = true;
		position.runAlgorithm(() -> {
			model.getHeaderModel().setTag(PGN.TAG_FEN, fen);
			position.setPositionSnapshot(newPos);
			model.getMoveModel().clear();
			fireMoveModelChanged();
			fireHeaderModelChanged();
		});
		ignoreNotifications = false;
	}

	/* At the moment this method does not protect its changes of the position by an algorithm.
	 * This could be necessary in the future. */
	public void setGameByDeepCopying(Game otherGame) throws InvalidFenException {
		if (this == otherGame) {
			return;
		}

		GameModel otherModel = otherGame.getModel();
		// This copy is needed, because the FEN header entry can be outdated. 
		Game copy = otherGame.getDeepCopy();
		copy.gotoStart();
		String fen = copy.getPosition().getFEN();
		Position newPos = new Position(fen, false); // If this throws, 'this' is unchanged!

		ignoreNotifications = true;
		position.runAlgorithm(() -> {
			cur = 0; // The order is important; this is always a valid value. 
			model.getHeaderModel().setByCopying(otherModel.getHeaderModel());
			model.getMoveModel().setByCopying(otherModel.getMoveModel());
			position.setPositionSnapshot(newPos);
			alwaysAddLine = false;
			fireHeaderModelChanged();
			fireMoveModelChanged();
		});
		ignoreNotifications = false;
	}

	public String getEvent() {
		return model.getHeaderModel().getEvent();
	}

	public String getSite() {
		return model.getHeaderModel().getSite();
	}

	public String getDate() {
		return model.getHeaderModel().getDate();
	}

	public String getRound() {
		return model.getHeaderModel().getRound();
	}

	public String getWhite() {
		return model.getHeaderModel().getWhite();
	}

	public String getBlack() {
		return model.getHeaderModel().getBlack();
	}

	public String getResultStr() {
		return model.getHeaderModel().getResultStr();
	}

	public String getWhiteEloStr() {
		return model.getHeaderModel().getWhiteEloStr();
	}

	public String getBlackEloStr() {
		return model.getHeaderModel().getBlackEloStr();
	}

	public String getEventDate() {
		return model.getHeaderModel().getEventDate();
	}

	public String getECO() {
		return model.getHeaderModel().getECO();
	}

	public int getResult() {
		return model.getHeaderModel().getResult();
	}

	public int getWhiteElo() {
		return model.getHeaderModel().getWhiteElo();
	}

	public int getBlackElo() {
		return model.getHeaderModel().getBlackElo();
	}

	public String getDateWithoutQuestionMarks() {
		String s = model.getHeaderModel().getDate();
		if (s.startsWith("?")) {
			return "";
		}
		if (s.contains(".?")) {
			s = s.substring(0, s.indexOf(".?"));
		}
		return s;
	}

	public String getRoundWithoutQuestionmark() {
		if (model.getHeaderModel().getRound() == null || model.getHeaderModel().getRound().equals("?")) {
			return "";
		}
		return model.getHeaderModel().getRound();
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
		return position.getVariant();
	}

	public void setChess960() {
		position.setChess960();
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
	 * split in two parts: (1) white and black player plus their elos, (2) event,
	 * site, date, round, and the ECO.
	 *
	 * @param line which line to return (0 or 1)
	 * @return the info string
	 */
	public String getHeaderString(int line) {
		if (line == 0) {
			StringBuilder sb = new StringBuilder();
			if (getWhite() == null) { // an empty game
				return sb.toString();
			}
			sb.append(getWhite());
			if (getWhiteElo() != 0) {
				sb.append(" [").append(getWhiteElo()).append("]");
			}
			sb.append(" - ").append(getBlack());
			if (getBlackElo() != 0) {
				sb.append(" [").append(getBlackElo()).append("]");
			}
			sb.append("  ").append(getResultStr());
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
			throw new IllegalArgumentException("Game::getHeaderString: Only two header lines supported");
		}
	}

	// ======================================================================
	// moves methods

	public boolean currentMoveHasNag(short nag) {
		return model.getMoveModel().hasNag(cur, nag);
	}

	public short[] getNags() {
		return model.getMoveModel().getNags(cur);
	}

	public void addNag(short nag) {
		model.getMoveModel().addNag(cur, nag);
		fireMoveModelChanged();
	}

	public void removeNag(short nag) {
		if (model.getMoveModel().removeNag(cur, nag)) {
			fireMoveModelChanged();
		}
	}

	public void removeAllNags() {
		if (model.getMoveModel().removeAllNags()) {
			fireMoveModelChanged();
		}
	}

	public void stripAll() {
		gotoStart();
		removeAllComments();
		deleteAllSublines();
		removeAllNags();
		fireMoveModelChanged();
	}

	// Comments:
	public boolean hasComment() {
		return model.getMoveModel().hasComment();
	}

	public String getPreMoveComment() {
		return model.getMoveModel().getPreMoveComment(cur);
	}

	public String getPostMoveComment() {
		return model.getMoveModel().getPostMoveComment(cur);
	}

	public void setPreMoveComment(String comment) {
		if (model.getMoveModel().setPreMoveComment(cur, comment)) {
			while (Move.isSpecial(model.getMoveModel().getMove(cur))) {
				++cur;
			}
		}
		fireMoveModelChanged();
	}

	public void setPostMoveComment(String comment) {
		model.getMoveModel().setPostMoveComment(cur, comment);
		fireMoveModelChanged();
	}

	public void appendPreMoveComment(String comment) {
		String currentComment = getPreMoveComment();
		if (currentComment == null || currentComment.isBlank()) {
			setPreMoveComment(comment);
		} else {
			setPreMoveComment(currentComment + " " + comment);
		}
	}

	public void appendPostMoveComment(String comment) {
		String currentComment = getPostMoveComment();
		if (currentComment == null || currentComment.isBlank()) {
			setPostMoveComment(comment);
		} else {
			setPostMoveComment(currentComment + " " + comment);
		}
	}

	public void removePreMoveComment() {
		removePreMoveComment(false);
	}

	private void removePreMoveComment(boolean silent) {
		String preComment = getPreMoveComment();
		if (preComment != null) {
			if (model.getMoveModel().removePreMoveComment(cur)) {
				// this removePreMoveComment does not change cur because only the comment is overwritten
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
		if (model.getMoveModel().removePostMoveComment(cur) && !silent) {
			fireMoveModelChanged();
		}
	}

	public void removeAllComments() {
		if (getNumOfPlies() == 0) {
			model.getMoveModel().setEmptyGameComment(null);
		} else {
			traverse(new TraverseAdapter() {
				@Override
				public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber,
						int level, String fenBeforeMove) {
					removePreMoveComment(true);
					removePostMoveComment(true);
				}
			}, true);
		}
		fireMoveModelChanged();
	}

	public String getEmptyGameComment() {
		return model.getMoveModel().getEmptyGameComment();
	}

	public void setEmptyGameComment(String comment) {
		model.getMoveModel().setEmptyGameComment(comment.trim());
		fireMoveModelChanged();
	}

	// ======================================================================

	public int getCurrentPly() {
		return position.getPlyNumber();
	}

	public int getCurrentMoveNumber() {
		return (position.getPlyNumber() + 1) / 2;
	}

	public int getNextMoveNumber() {
		return (position.getPlyNumber() + 2) / 2;
	}

	public int getPlyOffset() {
		return position.getPlyOffset();
	}

	public int getNumOfPlies() {
		int num = 0;
		int index = 0;
		while (model.getMoveModel().hasNextMove(index)) {
			index = model.getMoveModel().goForward(index);
			num++;
		}
		return num;
	}

	public int getNumOfMoves() {
		return Chess.plyToMoveNumber(getNumOfPlies() - 1);
	}

	public int getTotalNumOfPlies() {
		return model.getMoveModel().getTotalNumOfPlies();
	}

	// ======================================================================

	public Move getLastMove() {
		return position.getLastMove();
	}

	public String getLastMoveAsSanWithNumber() {
		return position.getLastMoveAsSanWithNumber();
	}

	public Move getNextMove() {
		return getNextMove(0);
	}

	public short getNextShortMove() {
		return getNextShortMove(0);
	}

	public Move getNextMove(int whichLine) {
		short moveAsShort = model.getMoveModel().getMove(model.getMoveModel().goForward(cur, whichLine));
		if (moveAsShort == GameMoveModel.NO_MOVE) {
			return null;
		}
		return position.getNextMove(moveAsShort);
	}

	public short getNextShortMove(int whichLine) {
		return model.getMoveModel().getMove(model.getMoveModel().goForward(cur, whichLine));
	}

	public boolean hasNextMove() {
		return model.getMoveModel().hasNextMove(cur);
	}

	public int getNumOfNextMoves() {
		return model.getMoveModel().getNumOfNextMoves(cur);
	}

	public short[] getNextShortMoves() {
		short[] moves = new short[model.getMoveModel().getNumOfNextMoves(cur)];
		for (int i = 0; i < moves.length; i++) {
			moves[i] = model.getMoveModel().getMove(model.getMoveModel().goForward(cur, i));
		}
		return moves;
	}

	public Move[] getNextMoves() {
		Move[] moves = new Move[model.getMoveModel().getNumOfNextMoves(cur)];
		for (int i = 0; i < moves.length; i++) {
			short moveAsShort = model.getMoveModel().getMove(model.getMoveModel().goForward(cur, i));
			moves[i] = position.getNextMove(moveAsShort);
		}
		return moves;
	}

	public boolean isMainLine() {
		return model.getMoveModel().isMainLine(cur);
	}

	// ======================================================================

	public boolean goBack() {
		if (DEBUG) {
			System.out.println("goBack");
		}

		int index = model.getMoveModel().goBack(cur, true);
		if (index != -1) {
			cur = index;
			ignoreNotifications = true;
			position.undoMove();
			ignoreNotifications = false;
			return true;
		} else {
			return false;
		}
	}

	public boolean goForward() {
		if (DEBUG) {
			System.out.println("goForward");
		}

		return goForward(0);
	}

	public boolean goForward(int whichLine) {
		if (DEBUG) {
			System.out.println("goForward " + whichLine);
		}

		int index = model.getMoveModel().goForward(cur, whichLine);
		short shortMove = model.getMoveModel().getMove(index);
		if (DEBUG) {
			System.out.println("  move = " + Move.getString(shortMove));
		}
		if (shortMove != GameMoveModel.NO_MOVE) {
			try {
				cur = index;
				ignoreNotifications = true;
				position.doMove(shortMove);
				ignoreNotifications = false;
				return true;
			} catch (IllegalMoveException ex) {
				System.out.println(model.toString() + " at move " + Move.getString(shortMove));
				ex.printStackTrace();
			}
		}
		return false;
	}

	public void gotoStart() {
		position.runAlgorithm(() -> {
			while (goBack()) {
			}
		});
	}

	public void gotoEnd() {
		position.runAlgorithm(() -> {
			while (!isMainLine()) {
				goBack();
			}
			while (goForward()) {
			}
		});
	}

	public void gotoEndOfLine() {
		position.runAlgorithm(() -> {
			while (goForward()) {
			}
		});
	}

	public void goBackToParentLine() {
		if (DEBUG) {
			System.out.println("goBackToMainLine");
		}

		position.runAlgorithm(() -> {
			goBackToLineBegin();
			goBack();
			goForward();
		});
	}

	public void goBackToLineBegin() {
		if (DEBUG) {
			System.out.println("goBackToLineBegin");
		}

		position.runAlgorithm(() -> {
			while (goBackInLine()) {
			}
		});
	}

	// Note: a node is not necessarily a move.
	public void gotoNode(int node) {
		int[] nodeNodes = getNodesToRoot(node);

		position.runAlgorithm(() -> {
			gotoStart();
			for (int i = nodeNodes.length - 2; i >= 0; i--) {
				int nextMoveIndex = 0;
				for (int j = 1; j < getNumOfNextMoves(); j++) {
					if (model.getMoveModel().goForward(cur, j) == nodeNodes[i]) {
						nextMoveIndex = j;
						break;
					}
				}
				goForward(nextMoveIndex);
			}
			cur = node; // now that we have made all the moves, set cur to node
		});
	}

	public void deleteCurrentLine() {
		position.runAlgorithm(() -> {
			goBackToLineBegin();
			int index = cur;
			goBack();
			if (0 == index) { // otherwise we get an exception in fireMoveModelChanged below
				model.getMoveModel().clear();
			} else {
				model.getMoveModel().deleteCurrentLine(index);
			}
			fireMoveModelChanged();
		});
	}

	public void deleteAllSublines() {
		position.runAlgorithm(() -> {
			// First, the position is placed in the only legal post-deletion state.
			gotoStart();
			if (model.getMoveModel().deleteAllSublines()) {
				fireMoveModelChanged();
			}
		});
	}

	public void deleteRemainingMoves() {
		if (model.getMoveModel().deleteRemainingMoves(cur)) {
			fireMoveModelChanged();
		}
	}

	public void gotoPly(int ply) {
		if (getCurrentPly() == ply) {
			return;
		}
		position.runAlgorithm(() -> {
			gotoStart();
			for (int i = 0; i < ply - getPlyOffset(); ++i) {
				goForward();
			}
		});
		// We check the value, because so many apps get it wrong.
		if (ply < getPlyOffset() || ply > getPlyOffset() + getNumOfPlies()) {
			System.err.println("Suspicious value in Game::gotoPly: ");
			System.err
					.println("   Requested ply: " + ply + ", plyOffset: " + getPlyOffset() + ", numOfPlies: " + getNumOfPlies());
		}
	}

	// ======================================================================

	private boolean goBackInLine() {
		if (DEBUG) {
			System.out.println("goBackInLine");
		}

		int index = model.getMoveModel().goBack(cur, false);
		if (index != -1) {
			cur = index; // needs to be set before undoing the move to allow
			// listeners to check for curNode
			ignoreNotifications = true;
			position.undoMove();
			ignoreNotifications = false;
			return true;
		} else {
			return false;
		}
	}

	private Move goForwardAndGetMove() {
		if (DEBUG) {
			System.out.println("goForwardAndGetMove");
		}

		return goForwardAndGetMove(0);
	}

	private Move goForwardAndGetMove(int whichLine) {
		if (DEBUG) {
			System.out.println("goForwardAndGetMove " + whichLine);
		}

		int index = model.getMoveModel().goForward(cur, whichLine);
		short shortMove = model.getMoveModel().getMove(index);
		if (DEBUG) {
			System.out.println("  move = " + Move.getString(shortMove));
		}
		if (shortMove != GameMoveModel.NO_MOVE) {
			try {
				cur = index;
				ignoreNotifications = true;
				position.doMove(shortMove);
				Move move = position.getLastMove();
				ignoreNotifications = false;
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
			node = model.getMoveModel().goBack(node, true);
			plies++;
		}
		return plies;
	}

	private int[] getNodesToRoot(int node) {
		int[] nodes;
		int i;
		if (model.getMoveModel().getMove(node) != GameMoveModel.NO_MOVE) {
			nodes = new int[getNumOfPliesToRoot(node) + 1];
			nodes[0] = node;
			i = 1;
		} else {
			nodes = new int[getNumOfPliesToRoot(node)];
			i = 0;
		}
		for (; i < nodes.length; i++) {
			node = model.getMoveModel().goBack(node, true);
			nodes[i] = node;
		}
		return nodes;
	}

	// ======================================================================

	public boolean promoteVariation() {
		if (isMainLine()) {
			return true;
		}
		int val = model.getMoveModel().promoteVariation(cur);
		if (val != -1) {
			cur = val;
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
	 * @param withLines whether to include sublines of the mainline.
	 */
	public void traverse(TraverseListener listener, boolean withLines) {
		position.runAlgorithm(() -> {
			int index = getCurNode();
			gotoStart();
			listener.initTraversal();
			if (!listener.stopRequested()) {
				traverse(listener, withLines, position.getPlyNumber(), 0);
			}
			try {
				gotoNode(index);
			} catch (IllegalArgumentException _) {
				// this exception can happen, if the TraverseListener changes the game and index becomes invalid
			}
		});
	}

	private void traverse(TraverseListener listener, boolean withLines, int plyNumber, int level) {
		while (hasNextMove() && !listener.stopRequested()) {
			int numOfNextMoves = getNumOfNextMoves();

			String fenBeforeMove = null;
			if (listener.notifyWithFen()) {
				fenBeforeMove = getPosition().getFEN();
			}
			Move move = goForwardAndGetMove();
			listener.notifyMove(move, getNags(), getPreMoveComment(), getPostMoveComment(), plyNumber, level, fenBeforeMove);

			if (withLines && numOfNextMoves > 1) {
				for (int i = 1; i < numOfNextMoves; i++) {
					goBack();
					listener.notifyLineStart(level);

					fenBeforeMove = null;
					if (listener.notifyWithFen()) {
						fenBeforeMove = getPosition().getFEN();
					}
					move = goForwardAndGetMove(i);
					listener.notifyMove(move, getNags(), getPreMoveComment(), getPostMoveComment(), plyNumber, level + 1,
							fenBeforeMove);

					traverse(listener, withLines, plyNumber + 1, level + 1);

					goBackToParentLine();
					listener.notifyLineEnd(level);
				}
			}

			plyNumber++;
		}
	}

	// ======================================================================
	// hashCode, equals and compareTo all depend on the model's methods.

	@Override
	public int hashCode() {
		return getModel().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof Game game)) {
			return false;
		}
		return game.getModel().equals(getModel());
	}

	@Override
	public int compareTo(Game other) {
		return getModel().compareTo(other.getModel());
	}

	// ======================================================================

	/**
	 * Returns whether the other game is completely contained in this game; header and
	 * move model are compared.
	 */
	public boolean contains(Game other) {
		if (this == other) {
			return true;
		}

		// check the header

		// check an empty game comment
		if ((other.getNumOfPlies() > getNumOfPlies()) || !model.getHeaderModel().contains(other.model.getHeaderModel())
				|| !checkComment(getEmptyGameComment(), other.getEmptyGameComment())) {
			return false;
		}
		// check the moves
		gotoStart();
		other.gotoStart();
		return checkMoves(this, other);
	}

	private static boolean checkMoves(Game game, Game other) {
		if ((game.isMainLine() != other.isMainLine()) || (game.getNumOfNextMoves() < other.getNumOfNextMoves())) {
			return false;
		}
		Move[] moves = game.getNextMoves();
		Move[] otherMoves = other.getNextMoves();
		for (Move otherMove : otherMoves) {
			boolean found = false;
			for (Move move : moves) {
				if (otherMove.equals(move)) {
					found = true;
					try {
						game.getPosition().doMove(move);
						other.getPosition().doMove(otherMove);
					} catch (IllegalMoveException _) {
						return false;
					}
					if (! checkComment(game.getPreMoveComment(), other.getPreMoveComment())
							|| !checkComment(game.getPostMoveComment(), other.getPostMoveComment())
							|| !checkNAGs(game.getNags(), other.getNags()) || !checkMoves(game, other)) {
						return false;
					}
					game.getPosition().undoMove();
					other.getPosition().undoMove();
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static boolean checkComment(String comment, String otherComment) {
		if (otherComment != null && !otherComment.isBlank()) {
			return comment != null && comment.contains(otherComment);
		}
		return true;
	}

	private static boolean checkNAGs(short[] nags, short[] otherNags) {
		if (otherNags == null) {
			return true;
		}
		if (nags == null) {
			return otherNags == null;
		}
		for (short otherNag : otherNags) {
			boolean found = false;
			for (short nag : nags) {
				if (otherNag == nag) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
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
		return model.toString();
	}

	// ======================================================================

	public void removeEvaluationNags() {
		if (model.getMoveModel().removeEvaluationNags(cur)) {
			fireMoveModelChanged();
		}
	}

	public void removePunctuationNags() {
		if (model.getMoveModel().removePunctuationNags(cur)) {
			fireMoveModelChanged();
		}
	}

	// ======================================================================

	// Add a result, if game end with mate or stalemate.
	public void updateResult() {
		gotoStart();
		gotoEndOfLine();
		Position pos = getPosition();
		if (pos.isMate()) {
			if (pos.getToPlay() == Chess.WHITE) {
				model.getHeaderModel().setTag(PGN.TAG_RESULT, PGN.RESULT_BLACK_WINS);
			} else {
				model.getHeaderModel().setTag(PGN.TAG_RESULT, PGN.RESULT_WHITE_WINS);
			}
		} else if (pos.isStaleMate()) {
			model.getHeaderModel().setTag(PGN.TAG_RESULT, PGN.RESULT_DRAW);
		}
	}

	// ======================================================================
	// RelatedGame:

	@Override
	public boolean checkCompatibility(Position pos) {
		return position == pos;
	}

	@Override
	public void positionChanged(ChangeType type, short move, String fen) {
		if (ignoreNotifications) {
			return;
		}
		if (type == ChangeType.MOVE_DONE) {
			if (!alwaysAddLine) {
				short[] moves = getNextShortMoves();
				for (int i = 0; i < moves.length; i++) {
					if (moves[i] == move) {
						cur = model.getMoveModel().goForward(cur, i);
						return;
					}
				}
			}
			cur = model.getMoveModel().appendAsRightMostLine(cur, move);
			fireMoveModelChanged();
		} else if (type == ChangeType.MOVE_UNDONE) {
			cur = model.getMoveModel().goBack(cur, true);
		} else if (type == ChangeType.START_POS_CHANGED) {
			if (fen == null) {
				System.err.println("Game::positionChanged: ChangeType START_POS_CHANGED, but no FEN string.");
				model.getHeaderModel().removeTag(PGN.TAG_FEN);
				return;
			}
			cur = 0;
			model.getMoveModel().clear();
			if (fen.equals(FEN.START_POSITION)) {
				model.getHeaderModel().removeTag(PGN.TAG_FEN);
			} else {
				model.getHeaderModel().setTag(PGN.TAG_FEN, fen);
			}
			// Don't change other things here, say the result, because that result in an order problem.
		}
	}

	// ======================================================================

	@Serial
	private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
		String str = s.readUTF();
		StringReader strReader = new StringReader(str);
		PGNReader pgnRreader = new PGNReader(strReader, "no file");

		// Before we can parse PGN data into this, we need to prepare it as if a standard constructor 
		// had been called! (Reflection allows to use constructors, but not for this.)
		Class<Game> c = Game.class;
		{
			Field f = c.getDeclaredField("model");
			f.setAccessible(true);
			f.set(this, new GameModel());
			f.setAccessible(false);
		}
		{
			Field f = c.getDeclaredField("position");
			f.setAccessible(true);
			f.set(this, Position.createInitialPosition());
			f.setAccessible(false);
		}

		position.addPositionListener(this);

		try {
			pgnRreader.parseGame(this);
		} catch (PGNSyntaxError e) {
			throw new IOException(e.getMessage());
		}
	}

	@Serial
	private synchronized void writeObject(java.io.ObjectOutputStream s) throws IOException {
		StringWriter buf = new StringWriter();
		PGNWriter writer = new PGNWriter(buf);
		writer.write(this);
		s.writeUTF(buf.toString());
	}

	// ======================================================================

	// For debug purposes: 

	public void dumpMoveModel(PrintStream out) {
		model.getMoveModel().write(out);
	}
}
