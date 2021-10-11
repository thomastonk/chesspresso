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
package chesspresso.position;

import chesspresso.Chess;
import chesspresso.Variant;

/**
 * An implementation of the position interface.
 *
 * The class is optimized for memory footprint. Each instance uses only 36 bytes
 * for internal representation (plus some overhead for java internals).
 *
 * @author Bernhard Seybold
 * 
 */
public class CompactPosition extends AbstractPosition {

	private final static int SQI_EP_SHIFT = 0, SQI_EP_MASK = 0x07F, // [0, 128[
			CASTLES_SHIFT = 7, CASTLES_MASK = 0x00F, // [0, 15]
			TO_PLAY_SHIFT = 11, TO_PLAY_MASK = 0x001, // [0 | 1]
			PLY_NUMBER_SHIFT = 12, PLY_NUMBER_MASK = 0x3FF, // [0, 1024[
			HALF_MOVE_CLOCK_SHIFT = 22, HALF_MOVE_CLOCK_MASK = 0xFF; // [0, 128[

	/*
	 * =============================================================================
	 */

	private final int[] m_stones; // 32 bytes
	private final int m_flags; // 4 bytes

	/*
	 * =============================================================================
	 */

	public CompactPosition(ImmutablePosition position) {
		m_stones = new int[Chess.NUM_OF_SQUARES / 8];
		for (int sqi = 0; sqi < Chess.NUM_OF_SQUARES; sqi += 8) {
			m_stones[sqi / 8] = (position.getStone(sqi) - Chess.NO_STONE) + (position.getStone(sqi + 1) - Chess.NO_STONE) << 4
					+ (position.getStone(sqi + 2) - Chess.NO_STONE) << 8 + (position.getStone(sqi + 3) - Chess.NO_STONE) << 12
							+ (position.getStone(sqi + 4) - Chess.NO_STONE) << 16
									+ (position.getStone(sqi + 5) - Chess.NO_STONE) << 20
											+ (position.getStone(sqi + 6) - Chess.NO_STONE) << 24
													+ (position.getStone(sqi + 7) - Chess.NO_STONE) << 28;
		}

		m_flags = ((position.getSqiEP() - Chess.NO_SQUARE) << SQI_EP_SHIFT) + (position.getCastles() << CASTLES_SHIFT)
				+ ((position.getToPlay() == Chess.WHITE ? 0 : 1) << TO_PLAY_SHIFT) + (position.getPlyNumber() << PLY_NUMBER_SHIFT)
				+ (position.getHalfMoveClock() << HALF_MOVE_CLOCK_SHIFT);
	}

	/*
	 * =============================================================================
	 */

	@Override
	public int getStone(int sqi) {
		return ((m_stones[sqi / 8] >> (4 * (sqi & 0x7))) & 0xF) + Chess.NO_STONE;
	}

	@Override
	public int getPiece(int sqi) {
		throw new RuntimeException("CompactPosition::getPiece: method has not been implemented yet.");
	}

	@Override
	public int getSqiEP() {
		return ((m_flags >> SQI_EP_SHIFT) & SQI_EP_MASK) + Chess.NO_SQUARE;
	}

	@Override
	public int getCastles() {
		return ((m_flags >> CASTLES_SHIFT) & CASTLES_MASK);
	}

	@Override
	public int getToPlay() {
		return ((m_flags >> TO_PLAY_SHIFT) & TO_PLAY_MASK) == 0 ? Chess.WHITE : Chess.BLACK;
	}

	@Override
	public int getPlyNumber() {
		return ((m_flags >> PLY_NUMBER_SHIFT) & PLY_NUMBER_MASK);
	}

	@Override
	public int getHalfMoveClock() {
		return ((m_flags >> HALF_MOVE_CLOCK_SHIFT) & HALF_MOVE_CLOCK_MASK);
	}

	@Override
	public int getPlyOffset() {
		throw new RuntimeException("CompactPosition::getPlyOffset: method has not been implemented yet.");
	}

	@Override
	public int getWhitesKingSquare() {
		throw new RuntimeException("CompactPosition::getWhitesKingSquare: method has not been implemented yet.");
	}

	@Override
	public int getBlacksKingSquare() {
		throw new RuntimeException("CompactPosition::getBlacksKingSquare: method has not been implemented yet.");
	}

	@Override
	public long getAllPawnsBB() {
		throw new RuntimeException("CompactPosition::getAllPawnsBB: method has not been implemented yet.");
	}

	@Override
	public long getWhitePawnsBB() {
		throw new RuntimeException("CompactPosition::getWhitePawnsBB: method has not been implemented yet.");
	}

	@Override
	public long getBlackPawnsBB() {
		throw new RuntimeException("CompactPosition::getBlackPawnsBB: method has not been implemented yet.");
	}

	@Override
	public boolean isCheck() {
		throw new RuntimeException("CompactPosition::isCheck: method has not been implemented yet.");
	}

	@Override
	public boolean isMate() {
		throw new RuntimeException("CompactPosition::isMate: method has not been implemented yet.");
	}

	@Override
	public boolean isStaleMate() {
		throw new RuntimeException("CompactPosition::isStaleMate: method has not been implemented yet.");
	}

	@Override
	public boolean isInsufficientMaterial() {
		throw new RuntimeException("CompactPosition::isInsufficientMaterial: method has not been implemented yet.");
	}

	@Override
	public int getNumberOfPieces() {
		throw new RuntimeException("CompactPosition::getNumberOfPieces: method has not been implemented yet.");
	}

	@Override
	public Variant getVariant() {
		throw new RuntimeException("CompactPosition::getVariant: method has not been implemented yet.");
	}

	@Override
	public int getChess960KingFile() {
		throw new RuntimeException("CompactPosition::getChess960KingFile: method has not been implemented yet.");
	}

	@Override
	public int getChess960QueensideRookFile() {
		throw new RuntimeException("CompactPosition::getChess960QueensideRookFile: method has not been implemented yet.");
	}

	@Override
	public int getChess960KingsideRookFile() {
		throw new RuntimeException("CompactPosition::getChess960KingsideRookFile: method has not been implemented yet.");
	}
}
