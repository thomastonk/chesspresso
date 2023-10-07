/*******************************************************************************
 * Copyright (C) 2019-2023 Thomas Niessen. All rights reserved.
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
package chesspresso.position.view;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.move.Move;

/*
 * A PieceTracker is used for visualization of the moves of some pieces during a game
 * or game fragment.
 */
public class PieceTracker {

	private final static boolean DEBUG = false;

	private final static Color FUTURE_COLOR_WHITE_PIECE = new Color(0, 253, 0); // almost full green 
	private final static Color PAST_COLOR_WHITE_PIECE = new Color(160, 160, 160); // a light grey 
	private final static Color FUTURE_COLOR_BLACK_PIECE = new Color(253, 200, 0); // almost full orange
	private final static Color PAST_COLOR_BLACK_PIECE = new Color(100, 100, 100); // a dark gray 

	private final Game game;
	private final int chess960QueensideRookFile; // a little bit of optimization
	private final int chess960KingsideRookFile;

	private final Set<Integer> startingSquares;

	public PieceTracker(Game game) {
		this.game = game;
		chess960QueensideRookFile = game.getPosition().getChess960QueensideRookFile();
		chess960KingsideRookFile = game.getPosition().getChess960KingsideRookFile();
		startingSquares = new HashSet<>();
	}

	public void addPiece(int currentSqi) {
		Integer square = computeStartingSquare(currentSqi);
		if (square != null) {
			startingSquares.add(square);
		}
	}

	public void removePiece(int currentSqi) {
		Integer square = computeStartingSquare(currentSqi);
		if (square != null) {
			startingSquares.remove(square);
		}
	}

	public void removeAllPieces() {
		startingSquares.clear();
	}

	private Integer computeStartingSquare(int sqi) {
		if (game.getPosition().getPiece(sqi) == Chess.NO_PIECE) {
			return null;
		}
		Game copy = game.getDeepCopy();
		copy.gotoNode(game.getCurNode());
		int currentSqi = sqi;
		Move move = copy.getPosition().getLastMove();
		while (copy.goBack()) {
			if (move.getToSqi() == currentSqi) {
				currentSqi = move.getFromSqi();
			} else if (move.isCastle()) { // add rook movement during castling
				int fromSqi = Chess.NO_SQUARE;
				boolean whiteMoved = copy.getPosition().getToPlay() == Chess.WHITE;
				if (whiteMoved && move.isLongCastle() && currentSqi == Chess.D1) {
					fromSqi = Chess.A1;
				} else if (whiteMoved && move.isShortCastle() && currentSqi == Chess.F1) {
					fromSqi = Chess.H1;
				} else if (!whiteMoved && move.isLongCastle() && currentSqi == Chess.D8) {
					fromSqi = Chess.A8;
				} else if (!whiteMoved && move.isShortCastle() && currentSqi == Chess.F8) {
					fromSqi = Chess.H8;
				}
				if (fromSqi != Chess.NO_SQUARE) {
					currentSqi = fromSqi;
				}
			} else if (move.isCastleChess960()) {
				int fromSqi = Chess.NO_SQUARE;
				boolean whiteMoved = copy.getPosition().getToPlay() == Chess.WHITE;
				if (whiteMoved && move.isLongCastle() && currentSqi == Chess.D1) {
					fromSqi = chess960QueensideRookFile;
				} else if (whiteMoved && move.isShortCastle() && currentSqi == Chess.F1) {
					fromSqi = chess960KingsideRookFile;
				} else if (!whiteMoved && move.isLongCastle() && currentSqi == Chess.D8) {
					fromSqi = chess960QueensideRookFile + Chess.A8;
				} else if (!whiteMoved && move.isShortCastle() && currentSqi == Chess.F8) {
					fromSqi = chess960KingsideRookFile + Chess.A8;
				}
				if (fromSqi != Chess.NO_SQUARE) {
					currentSqi = fromSqi;
				}
			}
			move = copy.getPosition().getLastMove();
		}
		return currentSqi;
	}

	public void updateDecorations(PositionView positionView) {
		positionView.removeAllPieceTracking(false);
		if (startingSquares.isEmpty()) {
			return;
		}
		Game copy = game.getDeepCopy();
		Set<Integer> currentStartingSquares = new HashSet<>();
		{ // add decorations for moves made in the past
			copy.gotoNode(game.getCurNode());
			List<Move> pastMoves = new ArrayList<>();
			Move move = copy.getLastMove();
			while (move != null) {
				pastMoves.add(move);
				copy.goBack();
				move = copy.getLastMove();
			}
			currentStartingSquares = new HashSet<>(startingSquares);
			// Make a reverse copy by hand. Java 21 will offer List.reverse(). 
			List<Move> pastMovesReversed = new ArrayList<>(pastMoves.size());
			for (int index = pastMoves.size() - 1; index >= 0; --index) {
				pastMovesReversed.add(pastMoves.get(index));
			}
			addDecorations(this, positionView, currentStartingSquares, pastMovesReversed, false);
		}
		{ // add decorations for moves to be made in the future
			copy.gotoNode(game.getCurNode());
			List<Move> futureMoves = new ArrayList<>();
			while (copy.goForward()) {
				futureMoves.add(copy.getLastMove());
			}
			addDecorations(this, positionView, currentStartingSquares, futureMoves, true);
		}
	}

	// Note: the set of tracked squares will be modified!
	private void addDecorations(PieceTracker pieceTracker, PositionView positionView, Set<Integer> trackedSquares,
			List<Move> moves, boolean isFutureMove) {
		for (Move move : moves) {
			if (DEBUG) {
				StringBuilder sb = new StringBuilder();
				sb.append("Tracked: ");
				for (Integer tS : trackedSquares) {
					sb.append(Chess.sqiToStr(tS)).append(" ");
				}
				sb.append(", next move: ").append(move.getLAN());
				System.err.println(sb.toString());
			}
			if (trackedSquares.contains(move.getFromSqi())) {
				positionView.addDecoration(DecorationFactory.getArrowDecoration(move.getFromSqi(), move.getToSqi(),
						getColor(move.isWhiteMove(), isFutureMove), pieceTracker), false);
				trackedSquares.remove(move.getFromSqi());
				trackedSquares.add(move.getToSqi());
			} else if (trackedSquares.contains(move.getToSqi())) { // a tracked piece is captured
				trackedSquares.remove(move.getToSqi());
			}
			// castling
			if (move.isCastle()) { // add rook movement during castling
				int fromSqi = Chess.NO_SQUARE;
				int toSqi = Chess.NO_SQUARE;
				boolean whiteMoved = move.isWhiteMove();
				if (whiteMoved && move.isLongCastle() && trackedSquares.contains(Chess.A1)) {
					fromSqi = Chess.A1;
					toSqi = Chess.D1;
				} else if (whiteMoved && move.isShortCastle() && trackedSquares.contains(Chess.H1)) {
					fromSqi = Chess.H1;
					toSqi = Chess.F1;
				} else if (!whiteMoved && move.isLongCastle() && trackedSquares.contains(Chess.A8)) {
					fromSqi = Chess.A8;
					toSqi = Chess.D8;
				} else if (!whiteMoved && move.isShortCastle() && trackedSquares.contains(Chess.H8)) {
					fromSqi = Chess.H8;
					toSqi = Chess.F8;
				}
				if (fromSqi != Chess.NO_SQUARE && toSqi != Chess.NO_SQUARE) {
					positionView.addDecoration(DecorationFactory.getArrowDecoration(fromSqi, toSqi,
							getColor(move.isWhiteMove(), isFutureMove), pieceTracker), false);
					trackedSquares.remove(fromSqi);
					trackedSquares.add(toSqi);
				}
			}
			// chess960 castling
			if (move.isCastleChess960()) {
				int fromSqi = Chess.NO_SQUARE;
				int toSqi = Chess.NO_SQUARE;
				boolean whiteMoved = move.isWhiteMove();
				if (whiteMoved && move.isLongCastle() && trackedSquares.contains(chess960QueensideRookFile)) {
					fromSqi = chess960QueensideRookFile;
					toSqi = Chess.D1;
				} else if (whiteMoved && move.isShortCastle() && trackedSquares.contains(chess960KingsideRookFile)) {
					fromSqi = chess960KingsideRookFile;
					toSqi = Chess.F1;
				} else if (!whiteMoved && move.isLongCastle() && trackedSquares.contains(chess960QueensideRookFile + Chess.A8)) {
					fromSqi = chess960QueensideRookFile + Chess.A8;
					toSqi = Chess.D8;
				} else if (!whiteMoved && move.isShortCastle() && trackedSquares.contains(chess960KingsideRookFile + Chess.A8)) {
					fromSqi = chess960QueensideRookFile + Chess.A8;
					toSqi = Chess.F8;
				}
				if (fromSqi != Chess.NO_SQUARE && toSqi != Chess.NO_SQUARE) {
					positionView.addDecoration(DecorationFactory.getArrowDecoration(fromSqi, toSqi,
							getColor(whiteMoved, isFutureMove), pieceTracker), false);
					trackedSquares.remove(fromSqi);
					trackedSquares.add(toSqi);
				}
			}
			// en passant
			if (move.isEPMove()) {
				boolean whiteMoved = move.isWhiteMove();
				if (whiteMoved && trackedSquares.contains(move.getToSqi() - 8)) {
					trackedSquares.remove(move.getToSqi() - 8);
				} else if (whiteMoved && trackedSquares.contains(move.getToSqi() + 8)) {
					trackedSquares.remove(move.getToSqi() + 8);
				}
			}
		}
	}

	private static Color getColor(boolean isWhiteStone, boolean isFutureMove) {
		if (isWhiteStone) {
			if (isFutureMove) {
				return FUTURE_COLOR_WHITE_PIECE;
			} else {
				return PAST_COLOR_WHITE_PIECE;
			}
		} else {
			if (isFutureMove) {
				return FUTURE_COLOR_BLACK_PIECE;
			} else {
				return PAST_COLOR_BLACK_PIECE;
			}
		}
	}
}
