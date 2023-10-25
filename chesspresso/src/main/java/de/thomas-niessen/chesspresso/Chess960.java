/*******************************************************************************
 * Copyright (C) 2020-2024 Thomas Niessen. All rights reserved.
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
package chesspresso;

public class Chess960 {

	private final static char[][] NRK = { // knights, rooks and king
			{ 'N', 'N', 'R', 'K', 'R' }, // 0 - 95
			{ 'N', 'R', 'N', 'K', 'R' }, // 96 - 191
			{ 'N', 'R', 'K', 'N', 'R' }, // 192 - 287
			{ 'N', 'R', 'K', 'R', 'N' }, // 288 - 383
			{ 'R', 'N', 'N', 'K', 'R' }, // 384 - 479
			{ 'R', 'N', 'K', 'N', 'R' }, // 480 - 575
			{ 'R', 'N', 'K', 'R', 'N' }, // 576 - 671
			{ 'R', 'K', 'N', 'N', 'R' }, // 672 - 767
			{ 'R', 'K', 'N', 'R', 'N' }, // 768 - 863
			{ 'R', 'K', 'R', 'N', 'N' } }; // 864 - 959

	private final static char[][] BB = { // both bishops
			{ 'B', 'B', '-', '-', '-', '-', '-', '-' }, // a, b
			{ 'B', '-', '-', 'B', '-', '-', '-', '-' }, // a, d
			{ 'B', '-', '-', '-', '-', 'B', '-', '-' }, // a, f
			{ 'B', '-', '-', '-', '-', '-', '-', 'B' }, // a, h
			{ '-', 'B', 'B', '-', '-', '-', '-', '-' }, // b, c
			{ '-', '-', 'B', 'B', '-', '-', '-', '-' }, // c, d
			{ '-', '-', 'B', '-', '-', 'B', '-', '-' }, // c, f
			{ '-', '-', 'B', '-', '-', '-', '-', 'B' }, // c, h
			{ '-', 'B', '-', '-', 'B', '-', '-', '-' }, // b, e
			{ '-', '-', '-', 'B', 'B', '-', '-', '-' }, // d, e
			{ '-', '-', '-', '-', 'B', 'B', '-', '-' }, // e, f
			{ '-', '-', '-', '-', 'B', '-', '-', 'B' }, // e, h
			{ '-', 'B', '-', '-', '-', '-', 'B', '-' }, // b, g
			{ '-', '-', '-', 'B', '-', '-', 'B', '-' }, // d, g
			{ '-', '-', '-', '-', '-', 'B', 'B', '-' }, // f, g
			{ '-', '-', '-', '-', '-', '-', 'B', 'B' } }; // g, h

	// The German Wikipedia entry on Chess960 describes two tables which together
	// describe all 960 starting position and also define a number for each
	// starting position. The first table describes the NRKQ pattern, i.e. the
	// distribution of knights, rooks, King and queen.
	// In this table, the queen's position follows a simple rule: it is
	// the repeating scheme: 0,1,2,3,4,5,0,1,2,3,4,5,...
	// So, here we need only the 10 NRK patterns and generate out of it the
	// 60 NRKQ patterns automatically.
	// The second table is BB, and it describes the positions of the bishops.
	// For each NRKQ pattern the 16 bishop patterns are inserted to create
	// the next 16 start positions.

	public static String getStartPattern(int posNumber) {
		if (posNumber < 0 || posNumber > 960) {
			throw new IllegalArgumentException("Chess960::getStartPattern: illegal position number " + posNumber);
		}
		int nrkRangeNumber = posNumber / 96;
		int qIndex = (posNumber % 96) / 16;
		int bPatternNumber = posNumber % 16;

		char[] nrkArray = NRK[nrkRangeNumber];
		char[] bbArray = BB[bPatternNumber];

		StringBuilder sb = new StringBuilder();
		int nrkqIndex = 0;
		for (int index = 0; index < 8; ++index) {
			if (bbArray[index] == 'B') {
				sb.append("B");
			} else {
				if (nrkqIndex == qIndex) {
					sb.append("Q");
				} else if (nrkqIndex < qIndex) {
					sb.append(nrkArray[nrkqIndex]);
				} else {
					sb.append(nrkArray[nrkqIndex - 1]);
				}
				++nrkqIndex;
			}
		}

		return sb.toString();
	}

	public static String getFEN(int posNumber) {
		if (posNumber < 0 || posNumber > 960) {
			throw new IllegalArgumentException("Chess960::getFEN: illegal position number " + posNumber);
		}
		return getFEN(getStartPattern(posNumber));
	}

	public static String getFEN(String pattern) {
		StringBuilder fen = new StringBuilder();
		fen.append(pattern.toLowerCase());
		fen.append("/pppppppp/8/8/8/8/PPPPPPPP/");
		fen.append(pattern);
		fen.append(" w ");
		int queensideRookIndex = pattern.indexOf('R');
		int kingsideRookIndex = pattern.lastIndexOf('R');
		fen.append((char) ('A' + queensideRookIndex));
		fen.append((char) ('A' + kingsideRookIndex));
		fen.append((char) ('a' + queensideRookIndex));
		fen.append((char) ('a' + kingsideRookIndex));
		fen.append(" - 0 1");

		return fen.toString();
	}

	public static boolean isValidPattern(String pattern) {
		if (pattern == null || pattern.length() != 8) {
			return false;
		}
		int kings = 0, queens = 0, rooks = 0, bishops = 0, knights = 0;
		int king = -1, firstRook = -1, secondRook = -1, firstBishop = -1, secondBishop = -1;
		for (int index = 0; index < 8; ++index) {
			switch (pattern.charAt(index)) {
				case 'K' -> {
					++kings;
					king = index;
				}
				case 'Q' -> ++queens;
				case 'R' -> {
					++rooks;
					if (firstRook == -1) {
						firstRook = index;
					} else {
						secondRook = index;
					}
				}
				case 'B' -> {
					++bishops;
					if (firstBishop == -1) {
						firstBishop = index;
					} else {
						secondBishop = index;
					}
				}
				case 'N' -> ++knights;
				default -> {
					return false;
				}
			}
		}
		if (kings != 1 || queens != 1 || rooks != 2 || bishops != 2 || knights != 2) {
			return false;
		}
		//noinspection RedundantIfStatement
		if (((secondBishop - firstBishop) % 2 == 0) || firstRook > king || secondRook < king) {
			return false;
		}
		return true;
	}

	public static boolean areBishopsValid(String pattern) {
		int firstBishop = -1;
		for (int index = 0; index < 8; ++index) {
			if (pattern.charAt(index) == 'B') {
				if (firstBishop == -1) {
					firstBishop = index;
				} else {
					if ((index - firstBishop) % 2 == 0) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public static boolean areKingAndRooksValid(String pattern) {
		int king = -1, firstRook = -1, secondRook = -1;
		for (int index = 0; index < 8; ++index) {
			char ch = pattern.charAt(index);
			if (ch == 'R') {
				if (firstRook == -1) {
					firstRook = index;
				} else {
					if (secondRook != -1 || king == -1) {
						return false;
					}
					secondRook = index;
				}
			} else if (ch == 'K') {
				if (firstRook == -1 || secondRook != -1) {
					return false;
				}
				king = index;
			}
		}
		return true;
	}

}
