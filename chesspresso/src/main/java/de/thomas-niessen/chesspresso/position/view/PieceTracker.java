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
import java.util.List;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.game.Game;
import chesspresso.move.Move;
import chesspresso.position.view.Decoration.DecorationType;

public class PieceTracker {

	private final static Color FUTURE_COLOR_WHITE_PIECE = new Color(0, 253, 0); // almost full green 
	private final static Color PAST_COLOR_WHITE_PIECE = new Color(160, 160, 160); // a light grey 
	private final static Color FUTURE_COLOR_BLACK_PIECE = new Color(253, 200, 0); // almost full orange
	private final static Color PAST_COLOR_BLACK_PIECE = new Color(100, 100, 100); // a dark gray 

	private record FromTo(int from, int to) {
	}

	public static void showTrack(int sqi, Game game, PositionView positionView) {
		int stone = game.getPosition().getStone(sqi);
		if (stone == Chess.NO_STONE) {
			return;
		}
		boolean isWhiteStone = Chess.stoneToColor(stone) == Chess.WHITE;
		Game copy = game.getDeepCopy();
		{ // future moves of the piece
			copy.gotoNode(game.getCurNode());
			List<FromTo> futureMoves = new ArrayList<>();
			int currentSqi = sqi;
			while (copy.goForward()) {
				Move move = copy.getPosition().getLastMove();
				if (move.getFromSqi() == currentSqi) { // the stone moves
					int toSqi = move.getToSqi();
					futureMoves.add(new FromTo(currentSqi, toSqi));
					currentSqi = toSqi;
				} else if (move.getToSqi() == currentSqi) { // the stone is captured
					break;
				} else if (move.isCastle()) { // add rook movement during castling
					if (copy.getVariant() == Variant.STANDARD) {
						int toSqi = Chess.NO_SQUARE;
						boolean whiteMoved = copy.getPosition().getToPlay() == Chess.BLACK;
						if (whiteMoved && move.isLongCastle() && currentSqi == Chess.A1) {
							toSqi = Chess.D1;
						} else if (whiteMoved && move.isShortCastle() && currentSqi == Chess.H1) {
							toSqi = Chess.F1;
						} else if (!whiteMoved && move.isLongCastle() && currentSqi == Chess.A8) {
							toSqi = Chess.D8;
						} else if (!whiteMoved && move.isShortCastle() && currentSqi == Chess.H8) {
							toSqi = Chess.F8;
						}
						if (toSqi != Chess.NO_SQUARE) {
							futureMoves.add(new FromTo(currentSqi, toSqi));
							currentSqi = toSqi;
						}
					} else if (copy.getVariant() == Variant.CHESS960) {
						int toSqi = Chess.NO_SQUARE;
						boolean whiteMoved = copy.getPosition().getToPlay() == Chess.BLACK;
						if (whiteMoved && move.isLongCastle()
								&& currentSqi == copy.getPosition().getChess960QueensideRookFile()) {
							toSqi = Chess.D1;
						} else if (whiteMoved && move.isShortCastle()
								&& currentSqi == copy.getPosition().getChess960KingsideRookFile()) {
							toSqi = Chess.F1;
						} else if (!whiteMoved && move.isLongCastle()
								&& currentSqi == copy.getPosition().getChess960QueensideRookFile() + Chess.A8) {
							toSqi = Chess.D8;
						} else if (!whiteMoved && move.isShortCastle()
								&& currentSqi == copy.getPosition().getChess960QueensideRookFile() + Chess.A8) {
							toSqi = Chess.F8;
						}
						if (toSqi != Chess.NO_SQUARE) {
							futureMoves.add(new FromTo(currentSqi, toSqi));
							currentSqi = toSqi;
						}
					}
				} else if (move.isEPMove()) {
					// break, if the stone was on the ep square
					boolean whiteMoved = copy.getPosition().getToPlay() == Chess.BLACK;
					if (whiteMoved && currentSqi == move.getToSqi() - 8) {
						break;
					} else if (whiteMoved && currentSqi == move.getToSqi() + 8) {
						break;
					}
				}
			}
			Color color = isWhiteStone ? FUTURE_COLOR_WHITE_PIECE : FUTURE_COLOR_BLACK_PIECE;
			addDecorations(positionView, futureMoves, color);
		}

		{ // past moves of the piece
			copy.gotoNode(game.getCurNode());
			List<FromTo> pastMoves = new ArrayList<>();
			int currentSqi = sqi;
			Move move = copy.getPosition().getLastMove();
			while (copy.goBack()) {
				if (move.getToSqi() == currentSqi) {
					int fromSqi = move.getFromSqi();
					pastMoves.add(new FromTo(fromSqi, currentSqi));
					currentSqi = fromSqi;
				} else if (move.isCastle()) { // add rook movement during castling
					if (copy.getVariant() == Variant.STANDARD) {
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
							pastMoves.add(new FromTo(fromSqi, currentSqi));
							currentSqi = fromSqi;
						}
					} else if (copy.getVariant() == Variant.CHESS960) {
						int fromSqi = Chess.NO_SQUARE;
						boolean whiteMoved = copy.getPosition().getToPlay() == Chess.WHITE;
						if (whiteMoved && move.isLongCastle() && currentSqi == Chess.D1) {
							fromSqi = copy.getPosition().getChess960QueensideRookFile();
						} else if (whiteMoved && move.isShortCastle() && currentSqi == Chess.F1) {
							fromSqi = copy.getPosition().getChess960KingsideRookFile();
						} else if (!whiteMoved && move.isLongCastle() && currentSqi == Chess.D8) {
							fromSqi = copy.getPosition().getChess960QueensideRookFile() + Chess.A8;
						} else if (!whiteMoved && move.isShortCastle() && currentSqi == Chess.F8) {
							fromSqi = copy.getPosition().getChess960KingsideRookFile() + Chess.A8;
						}
						if (fromSqi != Chess.NO_SQUARE) {
							pastMoves.add(new FromTo(fromSqi, currentSqi));
							currentSqi = fromSqi;
						}
					}
				}
				move = copy.getPosition().getLastMove();
			}
			Color color = isWhiteStone ? PAST_COLOR_WHITE_PIECE : PAST_COLOR_BLACK_PIECE;
			addDecorations(positionView, pastMoves, color);
		}
	}

	public static void removeAllTracks(PositionView positionView) {
		positionView.removeDecorations(DecorationType.ARROW, FUTURE_COLOR_WHITE_PIECE);
		positionView.removeDecorations(DecorationType.ARROW, PAST_COLOR_WHITE_PIECE);
		positionView.removeDecorations(DecorationType.ARROW, FUTURE_COLOR_BLACK_PIECE);
		positionView.removeDecorations(DecorationType.ARROW, PAST_COLOR_BLACK_PIECE);
	}

	private static void addDecorations(PositionView positionView, List<FromTo> list, Color color) {
		for (FromTo fromTo : list) {
			positionView.addDecoration(DecorationFactory.getArrowDecoration(fromTo.from, fromTo.to, color), false);
		}
	}
}
