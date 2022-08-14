/*******************************************************************************
 * Copyright (C) 2021-2022 Thomas Niessen. All rights reserved.
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
package chesspresso.position;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import chesspresso.Variant;
import chesspresso.game.RelatedGame;
import chesspresso.game.RelatedGame.ChangeType;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.PositionImpl.PosInternalState;

/**
 * Position is the public part of the whole position hierarchy. It handles the PositionListeners and delegates 
 * all functionality to a MoveablePosition object. (This MoveablePosition is a PositionImpl object in the
 * standard implementation, but a MoveablePosition is preferred for future use. For example, in the Shatranj
 * implementation a different kind of object was used.) 
 * 
 * The user can prevent the listeners from being informed of changes by using an Algorithm object in the method 
 * runAlgorithm. If so, the notification of the listeners is deactivated, and then the algorithm is run. 
 * Thereafter the notification of the listeners is activated again, unless the algorithm was run within another 
 * algorithm.
 * 
 * This class is not thread-safe.
 * 
 * @author Thomas Niessen
 *
 */
public final class Position implements MoveablePosition, Serializable {

	@Serial
	private static final long serialVersionUID = 2L;

	private final MoveablePosition impl;
	private final List<PositionListener> listeners = new ArrayList<>();
	private RelatedGame relatedGame = null;
	private int algorithmDepth;

	public interface Algorithm {
		void run();
	}

	public Position() {
		impl = new PositionImpl();
		algorithmDepth = 0;
	}

	public Position(String fen, boolean validate) throws InvalidFenException {
		this();
		impl.initFromFEN(fen, validate);
	}

	public Position(String fen) throws InvalidFenException {
		this(fen, true);
	}

	public Position(ImmutablePosition pos) {
		this();
		setPosition(pos);
	}

	/**
	 * This is a public method, but please don't use it! It shall only be used in the implementation of Game.
	 * 
	 * Note: A non-null related game can only be set once.
	 */
	public void setRelatedGame(RelatedGame relatedGame) {
		if (this.relatedGame != null) {
			throw new IllegalStateException("Position::setRelatedGame: the related game is already set!");
		}
		if (relatedGame == null) {
			return;
		}
		if (relatedGame.checkCompatibility(this)) {
			this.relatedGame = relatedGame;
		} else {
			throw new IllegalStateException("Position::setRelatedGame: the compatibility check failed!");
		}
	}

	/**
	 * This is a public method, but please don't use it! It shall only be used in the implementation of Game.
	 * Its only use is to delete unnecessary references and thereby give the GC a chance.
	 */
	public void unsetRelatedGame() {
		relatedGame = null;
	}

	public static Position createInitialPosition() {
		try {
			return new Position(FEN.START_POSITION, true);
		} catch (InvalidFenException ignore) {
			return null;
		}
	}

	/*
	 * With an Algorithm, the user can prevent the listeners from being notified until the very end.
	 */
	public void runAlgorithm(Algorithm alg) {
		increaseAlgorithmDepth();
		try {
			alg.run();
		} finally {
			decreaseAlgorithmDepth();
		}
	}

	private void decreaseAlgorithmDepth() {
		if (algorithmDepth == 1) {
			algorithmDepth = 0;
			firePositionChanged();
		} else {
			algorithmDepth = Math.max(0, algorithmDepth - 1);
			// Let algorithmDepth never fall below 0!
		}
	}

	private void increaseAlgorithmDepth() {
		++algorithmDepth;
	}

	public boolean isOutsideAlgorithm() {
		return algorithmDepth <= 0;
	}

	@Override
	public String getLastMoveAsSanWithNumber() {
		return impl.getLastMoveAsSanWithNumber();
	}

	@Override
	public Move getNextMove(short moveAsShort) {
		return impl.getNextMove(moveAsShort);
	}

	@Override
	public void clear() {
		impl.clear();
		firePositionChanged();
	}

	@Override
	public void initFromFEN(String fen, boolean validate) throws InvalidFenException {
		impl.initFromFEN(fen, validate);
		firePositionChanged(ChangeType.START_POS_CHANGED, Move.NO_MOVE, fen);
	}

	@Override
	public void setPosition(ImmutablePosition position) {
		impl.setPosition(position);
	}

	@Override
	public void setStart() {
		impl.setStart();
		firePositionChanged();
	}

	@Override
	public void setStone(int sqi, int stone) {
		if (getStone(sqi) != stone) {
			impl.setStone(sqi, stone);
			firePositionChanged();
		}
	}

	@Override
	public void setCastles(int castles) {
		if (getCastles() != castles) {
			impl.setCastles(castles);
			firePositionChanged();
		}
	}

	@Override
	public void setSqiEP(int sqiEP) {
		if (getSqiEP() != sqiEP) {
			impl.setSqiEP(sqiEP);
			firePositionChanged();
		}
	}

	@Override
	public void setToPlay(int toPlay) {
		if (getToPlay() != toPlay) {
			impl.setToPlay(toPlay);
			firePositionChanged();
		}
	}

	@Override
	public void toggleToPlay() {
		impl.toggleToPlay();
		firePositionChanged();
	}

	@Override
	public void setPlyOffset(int plyOffset) {
		if (getPlyOffset() != plyOffset) {
			impl.setPlyOffset(plyOffset);
			firePositionChanged();
		}
	}

	@Override
	public void setHalfMoveClock(int halfMoveClock) {
		if (getHalfMoveClock() != halfMoveClock) {
			impl.setHalfMoveClock(halfMoveClock);
			firePositionChanged();
		}
	}

	@Override
	public void setChess960() {
		if (getVariant() != Variant.CHESS960) {
			impl.setChess960();
			firePositionChanged();
		}
	}

	@Override
	public void setChess960CastlingFiles(int kingFile, int queensideRookFile, int kingsideRookFile) {
		if (getChess960KingFile() != kingFile || getChess960QueensideRookFile() != queensideRookFile
				|| getChess960KingsideRookFile() != kingsideRookFile) {
			impl.setChess960CastlingFiles(kingFile, queensideRookFile, kingsideRookFile);
			firePositionChanged();
		}
	}

	@Override
	public void moveAllUp() {
		impl.moveAllUp();
		firePositionChanged();
	}

	@Override
	public void moveAllDown() {
		impl.moveAllDown();
		firePositionChanged();
	}

	@Override
	public void moveAllLeft() {
		impl.moveAllLeft();
		firePositionChanged();
	}

	@Override
	public void moveAllRight() {
		impl.moveAllRight();
		firePositionChanged();
	}

	@Override
	public void invert() {
		impl.invert();
		firePositionChanged();
	}

	@Override
	public int getStone(int sqi) {
		return impl.getStone(sqi);
	}

	@Override
	public int getPiece(int sqi) {
		return impl.getPiece(sqi);
	}

	@Override
	public int getSqiEP() {
		return impl.getSqiEP();
	}

	@Override
	public int getCastles() {
		return impl.getCastles();
	}

	@Override
	public int getToPlay() {
		return impl.getToPlay();
	}

	@Override
	public int getPlyNumber() {
		return impl.getPlyNumber();
	}

	@Override
	public int getPlyOffset() {
		return impl.getPlyOffset();
	}

	@Override
	public int getHalfMoveClock() {
		return impl.getHalfMoveClock();
	}

	@Override
	public Validity getValidity() {
		return impl.getValidity();
	}

	@Override
	public String getFEN() {
		return impl.getFEN();
	}

	@Override
	public String getFEN(int numberOfParts) {
		return impl.getFEN(numberOfParts);
	}

	@Override
	public String getEpFEN() {
		return impl.getEpFEN();
	}

	@Override
	public boolean isStartPosition() {
		return impl.isStartPosition();
	}

	@Override
	public long getHashCode() {
		return impl.getHashCode();
	}

	@Override
	public int getWhitesKingSquare() {
		return impl.getWhitesKingSquare();
	}

	@Override
	public int getBlacksKingSquare() {
		return impl.getBlacksKingSquare();
	}

	@Override
	public long getAllPawnsBB() {
		return impl.getAllPawnsBB();
	}

	@Override
	public long getWhitePawnsBB() {
		return impl.getWhitePawnsBB();
	}

	@Override
	public long getBlackPawnsBB() {
		return impl.getBlackPawnsBB();
	}

	@Override
	public boolean isCheck() {
		return impl.isCheck();
	}

	@Override
	public boolean isMate() {
		return impl.isMate();
	}

	@Override
	public boolean isStaleMate() {
		return impl.isStaleMate();
	}

	@Override
	public boolean isInsufficientMaterial() {
		return impl.isInsufficientMaterial();
	}

	@Override
	public int getNumberOfPieces() {
		return impl.getNumberOfPieces();
	}

	@Override
	public Variant getVariant() {
		return impl.getVariant();
	}

	@Override
	public int getChess960KingFile() {
		return impl.getChess960KingFile();
	}

	@Override
	public int getChess960QueensideRookFile() {
		return impl.getChess960QueensideRookFile();
	}

	@Override
	public int getChess960KingsideRookFile() {
		return impl.getChess960KingsideRookFile();
	}

	@Override
	public void internalValidate() throws IllegalPositionException {
		impl.internalValidate();
	}

	@Override
	public void doMove(short move) throws IllegalMoveException {
		impl.doMove(move);
		firePositionChanged(ChangeType.MOVE_DONE, move, null);
	}

	@Override
	public void doMove(Move move) throws IllegalMoveException {
		impl.doMove(move);
		firePositionChanged(ChangeType.MOVE_DONE, move.getShortMoveDesc(), null);
	}

	@Override
	public short getLastShortMove() {
		return impl.getLastShortMove();
	}

	@Override
	public Move getLastMove() {
		return impl.getLastMove();
	}

	@Override
	public boolean canUndoMove() {
		return impl.canUndoMove();
	}

	@Override
	public boolean undoMove() {
		boolean retVal = impl.undoMove();
		if (retVal) {
			firePositionChanged(ChangeType.MOVE_UNDONE, Move.NO_MOVE, null);
		}
		return retVal;
	}

	@Override
	public boolean canRedoMove() {
		return impl.canRedoMove();
	}

	@Override
	public boolean redoMove() {
		boolean retVal = impl.redoMove();
		if (retVal) {
			firePositionChanged();
		}
		return retVal;
	}

	@Override
	public short getMove(int from, int to, int promoPiece) {
		return impl.getMove(from, to, promoPiece);
	}

	@Override
	public short[] getAllMoves() {
		return impl.getAllMoves();
	}

	@Override
	public short getPawnMove(int colFrom, int to, int promoPiece) {
		return impl.getPawnMove(colFrom, to, promoPiece);
	}

	@Override
	public short getPieceMove(int piece, int colFrom, int rowFrom, int to) {
		return impl.getPieceMove(piece, colFrom, rowFrom, to);
	}

	// everything for PositionListeners

	public final void addPositionListener(PositionListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
		// for initialization
		listener.positionChanged(this);
		// TODO This can be a bad call: In Game::setPosition is Position.transferAllPositionListeners called,
		// and hence the situation of game/old position/new position is still not consistent.
	}

	public final void removePositionListener(PositionListener listener) {
		listeners.remove(listener);
	}

	public static void transferAllPositionListeners(Position sourcePos, Position targetPos) {
		if (sourcePos != null && targetPos != null) {
			for (PositionListener listener : sourcePos.listeners) {
				targetPos.addPositionListener(listener);
			}
		}
		if (sourcePos != null) {
			sourcePos.listeners.clear();
		}
	}

	/*
	 * The following two firePositionChanged methods looks like a misconception, but
	 * in fact this is a well-thought concept. The only thing one has to keep in mind
	 * is to call the second one, if the RelatedGame needs to be informed. 
	 * Note that the two positionChanged methods come from different interfaces.
	 */

	// This is the fire method for all changes NOT relevant to RelatedGame.
	public void firePositionChanged() {
		if (isOutsideAlgorithm()) {
			for (PositionListener listener : listeners) {
				listener.positionChanged(this); // From the interface PositionListener!
			}
		}
	}

	// This is the fire method for all changes relevant to RelatedGame (and the PositionListeners
	// are informed, if necessary, as well).
	private void firePositionChanged(ChangeType type, short move, String fen) {
		// First: The order here is important, since PositionListeners may depend on relatedGame.
		// Second: The relatedGame is indeed notified during algorithms. (Otherwise it would be
		// necessary to inform RelatedGame about the algorithm end, which is impossible so far.)
		if (relatedGame != null) {
			relatedGame.positionChanged(type, move, fen); // From the interface RelatedGame!
		}
		firePositionChanged();
	}

	@Override
	public PosInternalState getInternalState() {
		return impl.getInternalState();
	}
}
