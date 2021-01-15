/*******************************************************************************
 * Copyright (C) 2021 Thomas Niessen. All rights reserved.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import chesspresso.Variant;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.PositionImpl.PosInternalState;
import chesspresso.position.PositionListener.ChangeType;

/**
 * Position is the public part of the whole position hierarchy. It handles the
 * PositionListners and delegates all functionality to a PositionImpl object.
 * 
 * The user can prevent the listeners from being informed of changes by telling
 * the Position object to be 'within an algorithm'. This is used to make a
 * number of changes of the Position object with only once notification of the
 * listeners at the very end. Such algorithms can be nested. Call the method
 * increaseAlgorithmDepth to start an algorithm, and call decreaseAlgorithm to
 * stop one. When the last algorithms is stopped, the listeners are notified
 * (even if no change has taken place).
 * 
 * 
 * This class is not thread-safe.
 * 
 * @author Thomas
 *
 */
public final class Position implements MoveablePosition, Serializable {

    private static final long serialVersionUID = 2L;

    private final PositionImpl impl;

    private int algorithmDepth;

    private final List<PositionListener> listeners = new ArrayList<>();

    public Position() {
	impl = new PositionImpl();
	algorithmDepth = 0;
    }

    public Position(String fen, boolean strict) throws IllegalArgumentException {
	this();
	FEN.initFromFEN(this, fen, strict);
    }

    public Position(String fen) throws IllegalArgumentException {
	this(fen, true);
    }

    public Position(ImmutablePosition pos) {
	this();
	setPosition(pos);
    }

    public static Position createInitialPosition() {
	return new Position(FEN.START_POSITION, true);
    }

    public void decreaseAlgorithmDepth() {
	if (algorithmDepth == 1) {
	    algorithmDepth = 0;
	    firePositionChanged();
	} else {
	    algorithmDepth = Math.max(0, algorithmDepth - 1);
	}
    }

    public void increaseAlgorithmDepth() {
	++algorithmDepth;
    }

    public boolean isOutsideAlgorithm() {
	return algorithmDepth <= 0;
    }

    public String getLastMoveAsSanWithNumber() {
	return impl.getLastMoveAsSanWithNumber();
    }

    public Move getNextMove(short moveAsShort) {
	try {
	    impl.doMove(moveAsShort);
	} catch (IllegalMoveException e) {
	    e.printStackTrace();
	    return null;
	}
	Move move = impl.getLastMove();
	impl.undoMove();
	return move;
    }

    // TN: Introduced for special purposes; could it replace clear()?!
    public void clearAll() {
	impl.clearAll();
	firePositionChanged();
    }

    @Override
    public void clear() {
	impl.clear();
	firePositionChanged();
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
	    fireSquareChanged();
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
    public void setPlyNumber(int plyNumber) {
	if (getPlyNumber() != plyNumber) {
	    impl.setPlyNumber(plyNumber);
	    firePositionChanged();
	}
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
    public void setVariant(Variant variant) {
	if (getVariant() != variant) {
	    impl.setVariant(variant);
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
	fireMoveDone(move);
    }

    @Override
    public void doMove(Move move) throws IllegalMoveException {
	impl.doMove(move);
	fireMoveDone(move.getShortMoveDesc());
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
	    fireMoveUndone();
	}
	return retVal;
    }

    @Override
    public boolean canRedoMove() {
	return impl.canRedoMove();
    }

    @Override
    public boolean redoMove() {
	return impl.redoMove();
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
	listener.positionChanged(ChangeType.OTHER, this, Move.NO_MOVE);
    }

    public final void removePositionListener(PositionListener listener) {
	listeners.remove(listener);
    }

    public final synchronized List<PositionListener> getPositionListeners() {
	return Collections.unmodifiableList(listeners);
    }

    private void fireSquareChanged() {
	if (isOutsideAlgorithm()) {
	    for (PositionListener listener : listeners) {
		listener.positionChanged(ChangeType.SQUARE_CHANGED, this, Move.NO_MOVE);
	    }
	}
    }

    private void fireMoveDone(short move) {
	if (isOutsideAlgorithm()) {
	    for (PositionListener listener : listeners) {
		listener.positionChanged(ChangeType.MOVE_DONE, this, move);
	    }
	}
    }

    private void fireMoveUndone() {
	if (isOutsideAlgorithm()) {
	    for (PositionListener listener : listeners) {
		listener.positionChanged(ChangeType.MOVE_UNDONE, this, Move.NO_MOVE);
	    }
	}
    }

    private void firePositionChanged() {
	if (isOutsideAlgorithm()) {
	    for (PositionListener listener : listeners) {
		listener.positionChanged(ChangeType.OTHER, this, Move.NO_MOVE);
	    }
	}
    }

    public PosInternalState getInternalState() {
	return impl.getInternalState();
    }
}
