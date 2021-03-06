/*******************************************************************************
 * Basic version: Copyright (C) 2003 Bernhard Seybold. All rights reserved.
 * All changes since then: Copyright (C) 2019 Thomas Niessen. All rights reserved.
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

import chesspresso.Chess;

/**
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.1 $
 */
public abstract class AbstractPosition implements ImmutablePosition {

	/* ================================================================================ */
	// hash codes
	//
	// 6 5 4 3 2 1
	// 3210987654321098765432109876543210987654321098765432109876543210
	// 0xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxeeeeccccpxxxxxxxxxxxxxxxxxxxxxxx
	//
	// p = to play: 0 = white, 1 = black
	// c = castle: see castle constants 0 - 15
	// e = en passant column (0-7) + 1, 0 = no ep square
	// x = hash modifier according to stone, square
	//
	// the empty position (wtm, no castles, no ep, no pieces) is initialized to be 0
	// this position is illegal such that it will never occur in a game.
	//
	// the highest bit is always zero such that all (long) hash codes are > 0
	//
	// the deterministic parts (e, c, p) are put into the middle to allow
	// hash table to index with up to the lowest 22 or highest 32 bits
	// highest bits are used for sorted hash tables
	//
	// TODO: add half move clock to hash code?
	// + correctness in endgames
	// - cannot reuse hashed positions if half move clock doesn't matter
	//
	// TODO: don't reserve special bits for ep and castles but xor them as well
	protected static long
//        HASH_ALL_MASK       = 0x7FFFFFFF007FFFFFL,
	HASH_ALL_MASK = 0x7FFFFFFFFF7FFFFFL, HASH_TOPLAY_MASK = 0x7FFFFFFFFF7FFFFFL, HASH_TOPLAY_MULT = 0x800000L;
//        HASH_CASTLE_MASK    = 0x7FFFFFFFF0FFFFFFL,
//        HASH_CASTLE_MULT    =          0x1000000L,
//        HASH_ENPASSANT_MASK = 0x7FFFFFFF0FFFFFFFL,
//        HASH_ENPASSANT_MULT =         0x10000000L;

	protected static long[][] s_hashMod;
	protected static long[] s_hashCastleMod;
	protected static long[] s_hashEPMod;

	static {
//        Random random = new Random(100);
//        s_hashMod = new long[Chess.NUM_OF_SQUARES][];
//        for (int i=0; i<Chess.NUM_OF_SQUARES; i++) {
//            s_hashMod[i] = new long[Chess.MAX_STONE - Chess.MIN_STONE + 1];
//            for (int j=0; j<Chess.MAX_STONE - Chess.MIN_STONE; j++) {
//                s_hashMod[i][j] = random.nextLong() & HASH_ALL_MASK;
//            }
//        }
		long randomNumber = 100L;

		s_hashMod = new long[Chess.NUM_OF_SQUARES][];
		for (int i = 0; i < Chess.NUM_OF_SQUARES; i++) {
			s_hashMod[i] = new long[Chess.MAX_STONE - Chess.MIN_STONE + 1];
			for (int j = 0; j < Chess.MAX_STONE - Chess.MIN_STONE + 1; j++) {
				// this is how random is implemented, except that random is masked
				// to 48 significant bits only (NSA?)
				// we re-implemented it here to guarantee that the implementation does
				// not change since hash keys might be externalized
				randomNumber = (randomNumber * 0x5DEECE66DL + 0xBL);
				s_hashMod[i][j] = randomNumber & HASH_ALL_MASK;
//                System.out.println(s_hashMod[i][j]);
//                if ((s_hashMod[i][j] & HASH_ALL_MASK) != s_hashMod[i][j]) System.out.println("Was ist los " + i + " " + j + " " + (s_hashMod[i][j] & HASH_ALL_MASK));
			}
		}

		s_hashCastleMod = new long[16];
		s_hashCastleMod[0] = 0L; // NO_CASTLES -> no change in hashCode
		for (int i = 1; i < 16; i++) {
			randomNumber = (randomNumber * 0x5DEECE66DL + 0xBL);
			s_hashCastleMod[i] = randomNumber & HASH_ALL_MASK;
		}

		s_hashEPMod = new long[8]; // sqiEP == NO_SQUARE -> must be 0
		for (int i = 0; i < 8; i++) {
			randomNumber = (randomNumber * 0x5DEECE66DL + 0xBL);
			s_hashEPMod[i] = randomNumber & HASH_ALL_MASK;
		}
	}

	private static long s_startPositionHashCode = 0L;

	protected static long getStartPositionHashCode() {
		// must be done after the bitboards are initialized in ChPosition -> cannot do it in
		// static of ChAbstractPosition
		if (s_startPositionHashCode == 0L) {
			AbstractMutablePosition startPos = new LightWeightPosition();
			FEN.initFromFEN(startPos, FEN.START_POSITION);
			s_startPositionHashCode = new Position(startPos).getHashCode(); // do after bitBoard init
		}
		return s_startPositionHashCode;
	}

	public static boolean isWhiteToPlay(long hashCode) {
		return (hashCode & HASH_TOPLAY_MULT) == 0L;
	}

	/* ================================================================================ */

	public long getHashCode() {
		/*---------- squares ----------*/
		long hashCode = 0L;
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
			int stone = getStone(sqi);
			if (stone != Chess.NO_STONE) {
				hashCode ^= s_hashMod[sqi][stone - Chess.MIN_STONE];
			}
		}

		/*---------- castles ----------*/
//        System.out.println(getCastles());
		hashCode ^= s_hashCastleMod[getCastles()];

		/*---------- en passant square ----------*/
		int sqiEP = getSqiEP();
		if (sqiEP != Chess.NO_SQUARE) {
			int col = Chess.sqiToCol(sqiEP);
			if (sqiEP < Chess.A4) { // do not use to play
				if ((col == 0 || getStone(Chess.coorToSqi(col - 1, 3)) != Chess.BLACK_PAWN)
						&& (col == 7 || getStone(Chess.coorToSqi(col + 1, 3)) != Chess.BLACK_PAWN)) {
					sqiEP = Chess.NO_COL;
				}
			} else {
				if ((col == 0 || getStone(Chess.coorToSqi(col - 1, 4)) != Chess.WHITE_PAWN)
						&& (col == 7 || getStone(Chess.coorToSqi(col + 1, 4)) != Chess.WHITE_PAWN)) {
					sqiEP = Chess.NO_COL;
				}
			}
		}
		if (sqiEP != Chess.NO_COL)
			hashCode ^= s_hashEPMod[Chess.sqiToCol(sqiEP)];

		/*---------- to play ----------*/
		if (getToPlay() == Chess.BLACK)
			hashCode |= HASH_TOPLAY_MULT;

		return hashCode;
	}

	public final int hashCode() {
		return (int) getHashCode();
	}

	public final boolean isStartPosition() {
		return getHashCode() == getStartPositionHashCode();
	}

	public boolean equals(Object obj) {
		return (obj instanceof ImmutablePosition) && (((ImmutablePosition) obj).getHashCode() == getHashCode());
	}

	/* ================================================================================ */

	public String getFEN() {
		return FEN.getFEN(this);
	}

	public String getFEN(int numberOfParts) {
		return FEN.getFEN(this, numberOfParts);
	}

	/* ================================================================================ */

	public boolean isCastlePossible(int castle) {
		if (castle == WHITE_SHORT_CASTLE) {
			return getStone(Chess.E1) == Chess.WHITE_KING && getStone(Chess.H1) == Chess.WHITE_ROOK;
		} else if (castle == WHITE_LONG_CASTLE) {
			return getStone(Chess.E1) == Chess.WHITE_KING && getStone(Chess.A1) == Chess.WHITE_ROOK;
		} else if (castle == BLACK_SHORT_CASTLE) {
			return getStone(Chess.E8) == Chess.BLACK_KING && getStone(Chess.H8) == Chess.BLACK_ROOK;
		} else if (castle == BLACK_LONG_CASTLE) {
			return getStone(Chess.E8) == Chess.BLACK_KING && getStone(Chess.A8) == Chess.BLACK_ROOK;
		} else {
			return false;
		}
	}

	public boolean isSquarePossibleEPSquare(int sqi) {
		if (getToPlay() == Chess.WHITE) {
			// white to play -> sqi ep caused by black pawn
			return Chess.sqiToRow(sqi) == 5 && getStone(sqi + Chess.NUM_OF_COLS) == Chess.NO_STONE
					&& getStone(sqi) == Chess.NO_STONE && getStone(sqi - Chess.NUM_OF_COLS) == Chess.BLACK_PAWN;
		} else {
			return Chess.sqiToRow(sqi) == 2 && getStone(sqi - Chess.NUM_OF_COLS) == Chess.NO_STONE
					&& getStone(sqi) == Chess.NO_STONE && getStone(sqi + Chess.NUM_OF_COLS) == Chess.WHITE_PAWN;
		}
	}

	public boolean isLegal() {
		/*---------- check to play ----------*/
		if (getToPlay() != Chess.WHITE && getToPlay() != Chess.BLACK)
			return false;

		/*---------- check ply number ----------*/
		if (getPlyNumber() < 0)
			return false;
		if (getHalfMoveClock() > getPlyNumber())
			return false;

		/*---------- check sqi ep ----------*/
		if (getSqiEP() != Chess.NO_SQUARE) {
			if (getToPlay() == Chess.WHITE) {
				if (getStone(getSqiEP() - Chess.NUM_OF_COLS) != Chess.pieceToStone(Chess.PAWN, Chess.BLACK))
					return false;
			} else {
				if (getStone(getSqiEP() + Chess.NUM_OF_COLS) != Chess.pieceToStone(Chess.PAWN, Chess.WHITE))
					return false;
			}
		}

		/*---------- check number of kings ----------*/
		int numOfWhiteKings = 0, numOfBlackKings = 0;
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
			if (getStone(sqi) == Chess.WHITE_KING)
				numOfWhiteKings++;
			if (getStone(sqi) == Chess.BLACK_KING)
				numOfBlackKings++;
		}
		if (numOfWhiteKings != 1 || numOfBlackKings != 1)
			return false;

		return true;
	}

	// TN changed three RuntimeExceptions to IllegalPositionExceptions and put hashCode condition
	// from the beginning to the end:
	public void validate() throws IllegalPositionException {
		int numOfWhiteKings = 0;
		int numOfBlackKings = 0;
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi++) {
			if (getStone(sqi) == Chess.WHITE_KING)
				numOfWhiteKings++;
			if (getStone(sqi) == Chess.BLACK_KING)
				numOfBlackKings++;
		}
		if (numOfWhiteKings != 1)
			throw new IllegalPositionException("Wrong number of white kings: " + numOfWhiteKings);
		if (numOfBlackKings != 1)
			throw new IllegalPositionException("Wrong number of black kings: " + numOfBlackKings);

		if (getToPlay() != Chess.WHITE && getToPlay() != Chess.BLACK) {
			throw new IllegalPositionException("Illegal to play: " + getToPlay());
		}

		long hashCode = getHashCode();
		if (hashCode <= 0) {
			throw new IllegalPositionException("Hashcode is " + hashCode + ". Should be > 0.");
		}
	}

	/* ================================================================================ */

	public String toString() {
		return FEN.getFEN(this);
	}

}
