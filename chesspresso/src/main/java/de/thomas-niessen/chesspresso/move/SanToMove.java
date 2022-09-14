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
package chesspresso.move;

import chesspresso.Chess;
import chesspresso.Variant;
import chesspresso.position.Position;

public class SanToMove {

	/**
	 * This method is build according to PGNReader::getLastTokenAsMove.
	 * 
	 * @param position
	 * @param san
	 * @return
	 */
	public static short getMove(Position position, String san) {

		String s = san.trim();
		char[] buffer = s.toCharArray();
		int length = buffer.length;

		int next = 0;
		int last = length - 1;
		if (buffer[last] == '+') {
			last--;
		} else if (buffer[last] == '#') {
			last--;
		}

		short move = Move.ILLEGAL_MOVE;
		Variant variant = position.getVariant();
		if (buffer[0] == 'O' && buffer[1] == '-' && buffer[2] == 'O') {
			if (length >= 5 && buffer[3] == '-' && buffer[4] == 'O') {
				if (variant == Variant.STANDARD) {
					move = Move.getLongCastle(position.getToPlay());
				} else {
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 5;
			} else if (length >= 3) {
				if (variant == Variant.STANDARD) {
					move = Move.getShortCastle(position.getToPlay());
				} else {
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960KingsideRookFile());
				}
				next = 3;
			} else {
				return Move.ILLEGAL_MOVE;
			}
		} else if (buffer[0] == '0' && buffer[1] == '-' && buffer[2] == '0') {
			if (length >= 5 && buffer[3] == '-' && buffer[4] == '0') { // Castles with zeros
				if (variant == Variant.STANDARD) {
					move = Move.getLongCastle(position.getToPlay());
				} else {
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 5;
			} else if (length >= 3) { // Castles with zeros 
				if (variant == Variant.STANDARD) {
					move = Move.getShortCastle(position.getToPlay());
				} else {
					move = Move.getChess960Castle(position.getToPlay(), position.getChess960KingFile(),
							position.getChess960QueensideRookFile());
				}
				next = 3;
			} else {
				return Move.ILLEGAL_MOVE;
			}
		} else if (buffer[0] == '-' && buffer[1] == '-') { // null move code
			move = Move.getNullMove();
		} else if (buffer[0] == 'Z' && buffer[1] == '0') { // null move code for CA
			move = Move.getNullMove();
		} else {
			char ch = buffer[0];
			if (ch >= 'a' && ch <= 'h') {
				/*---------- pawn move ----------*/
				int col = Chess.NO_COL;
				if (1 > last) {
					return Move.ILLEGAL_MOVE;
				}
				if (buffer[1] == 'x') {
					col = Chess.charToCol(ch);
					next = 2;
				}

				if (buffer[1] >= 'a' && buffer[1] <= 'h') { // the 'x' is missing!
					col = Chess.charToCol(ch);
					next = 1;
				}

				if (next + 1 > last) {
					return Move.ILLEGAL_MOVE;
				}
				int toSqi = Chess.strToSqi(buffer[next], buffer[next + 1]);
				next += 2;

				int promo = Chess.NO_PIECE;
				if (next <= last && buffer[next] == '=') {
					if (next < last) {
						promo = Chess.charToPiece(buffer[next + 1]);
					} else {
						return Move.ILLEGAL_MOVE;
					}
				}
				move = position.getPawnMove(col, toSqi, promo);
			} else {
				/*---------- non-pawn move ----------*/
				int piece = Chess.charToPiece(ch);

				if (last < 2) {
					return Move.ILLEGAL_MOVE;
				}
				int toSqi = Chess.strToSqi(buffer[last - 1], buffer[last]);
				last -= 2;

				if (buffer[last] == 'x')
				 {
					last--; // capturing
				}

				int row = Chess.NO_ROW, col = Chess.NO_COL;
				while (last >= 1) {
					char rowColChar = buffer[last];
					int r = Chess.charToRow(rowColChar);
					if (r != Chess.NO_ROW) {
						row = r;
					} else {
						int c = Chess.charToCol(rowColChar);
						if (c != Chess.NO_COL) {
							col = c;
						}
					}
					last--;
				}
				move = position.getPieceMove(piece, col, row, toSqi);
			}
		}
		return move;
	}
}
