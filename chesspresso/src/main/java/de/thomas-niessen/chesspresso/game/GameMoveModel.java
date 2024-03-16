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

import java.io.PrintStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
//import java.util.*;

import chesspresso.move.Move;
//import chesspresso.*;
import chesspresso.position.NAG;

/**
 * Representation of moves of a chess game.
 *
 * @author Bernhard Seybold
 */
final class GameMoveModel implements Comparable<GameMoveModel>, Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private final static boolean DEBUG = false;
	private final static boolean EXTRA_CHECKS = true;

	// ======================================================================

	final static int MODE_EVERYTHING = 0;

	final static short NO_MOVE = Move.NO_MOVE, LINE_START = Move.OTHER_SPECIALS, LINE_END = Move.OTHER_SPECIALS + 1,
			PRE_COMMENT_START = Move.OTHER_SPECIALS + 2, PRE_COMMENT_END = Move.OTHER_SPECIALS + 3,
			POST_COMMENT_START = Move.OTHER_SPECIALS + 4, POST_COMMENT_END = Move.OTHER_SPECIALS + 5,
			NAG_BASE = Move.OTHER_SPECIALS + 16, LAST_SPECIAL = (short) (NAG_BASE + NAG.NUM_OF_NAGS);

	// If LAST_SPECIAL is changed, uncomment this:
	//	static {
	//		if (LAST_SPECIAL > Move.SPECIAL_MOVE + Move.NUM_OF_SPECIAL_MOVES) {
	//			throw new RuntimeException("Not enough space to define special moves for game move model");
	//		}
	//	}

	// ======================================================================

	private short[] nodes;
	private int size;
	private int hashCode;
	private boolean hasComment;

	// ======================================================================

	GameMoveModel() {
		nodes = new short[32];
		nodes[0] = LINE_START;
		nodes[1] = LINE_END;
		size = 2;
		hashCode = 0;
		hasComment = false;
	}

	void clear() {
		Arrays.fill(nodes, (short) 0);
		nodes[0] = LINE_START;
		nodes[1] = LINE_END;
		size = 2;
		hashCode = 0;
		hasComment = false;
	}

	GameMoveModel getDeepCopy() {
		GameMoveModel copy = new GameMoveModel();
		copy.setByCopying(this);

		return copy;
	}

	void setByCopying(GameMoveModel otherModel) {
		if (this == otherModel) {
			return;
		}
		nodes = Arrays.copyOf(otherModel.nodes, otherModel.nodes.length);
		size = otherModel.size;
		hashCode = otherModel.hashCode;
		hasComment = otherModel.hasComment;
	}

	// ======================================================================
	// invariant checking

	private void checkLegalCursor(int index) {
		if (index < 0) {
			throw new IllegalArgumentException("Illegal index " + index);
		}
		if (index >= size) {
			throw new IllegalArgumentException("Illegal index " + index + " size=" + size);
		}
		if (nodes[index] != LINE_START && !isMoveValue(nodes[index])) {
			throw new IllegalArgumentException("No move at index " + index + " (value=" + valueToString(nodes[index]) + ")");
		}
	}

	// ======================================================================

	private static boolean isMoveValue(short value) {
		return !Move.isSpecial(value);
	}

	private static boolean isNagValue(short value) {
		return value >= NAG_BASE && value < NAG_BASE + NAG.NUM_OF_NAGS;
	}

	private static short getNagForValue(short value) {
		return (short) (value - NAG_BASE);
	}

	private static short getValueForNag(short nag) {
		return (short) (nag + NAG_BASE);
	}

	// ======================================================================

	private void changed() {
		hashCode = 0;
	}

	// ======================================================================

	boolean hasNag(int index, short nag) {
		if (DEBUG) {
			System.out.println("hasNag " + index + " nag " + nag);
			write(System.out);
		}

		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}

		short nagValue = getValueForNag(nag);
		short value;
		do {
			index++;
			value = nodes[index];
			if (value == nagValue) {
				return true;
			}
		} while (isNagValue(value));

		return false;
	}

	short[] getNags(int index) {
		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return null;
			}
		}
		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index])) {
				throw new IllegalArgumentException("No move at index " + index + " move=" + valueToString(nodes[index]));
			}
		}

		int num = 0;
		while (isNagValue(nodes[index + 1])) {
			index++;
			num++;
		}
		if (num == 0) {
			return null;
		} else {
			short[] nags = new short[num];
			// collect nags from back to front (most recently added last)
			for (int i = 0; i < num; i++) {
				nags[i] = getNagForValue(nodes[index - i]);
			}
			Arrays.sort(nags); // in order to get !,? etc before =, +- etc
			return nags;
		}
	}

	void addNag(int index, short nag) {
		if (DEBUG) {
			System.out.println("addNag " + index + " nag " + nag);
			write(System.out);
		}

		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return;
			}
		}

		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index])) {
				throw new IllegalArgumentException("No move at index " + index + " val=" + valueToString(nodes[index]));
			}
		}

		makeSpace(index + 1, 1, false); // most recent nag first
		nodes[index + 1] = getValueForNag(nag);
		changed();

		if (DEBUG) {
			write(System.out);
		}
	}

	boolean removeNag(int index, short nag) {
		if (DEBUG) {
			System.out.println("removeNag " + index + " nag " + nag);
			write(System.out);
		}

		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}

		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index])) {
				throw new IllegalArgumentException("No move at index " + index + " val=" + valueToString(nodes[index]));
			}
		}

		short nagValue = getValueForNag(nag);
		short value;
		boolean changed = false;
		do {
			index++;
			value = nodes[index];
			if (value == nagValue) {
				while (isNagValue(nodes[index + 1])) {
					nodes[index] = nodes[index + 1];
					index++;
				}
				nodes[index] = NO_MOVE;
				changed = true;
				break;
			}
		} while (isNagValue(value));
		changed();

		if (DEBUG) {
			write(System.out);
		}
		return changed;
	}

	boolean removePunctuationNags(int index) {
		boolean changed = false;
		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}
		for (short nag = NAG.PUNCTUATION_NAG_BEGIN; nag <= NAG.PUNCTUATION_NAG_END; ++nag) {
			if (removeNag(index, nag)) {
				changed = true;
			}
		}
		return changed;
	}

	boolean removeEvaluationNags(int index) {
		boolean changed = false;
		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}
		for (short nag = NAG.EVALUATION_NAG_BEGIN; nag <= NAG.EVALUATION_NAG_END; ++nag) {
			if (removeNag(index, nag)) {
				changed = true;
			}
		}
		return changed;
	}

	boolean removeAllNags() {
		// This simple method has caused many problems; change with utmost care.
		boolean changed = false;
		for (int index = 0; index < size; ++index) {
			if (nodes[index] == PRE_COMMENT_START) {
				while (nodes[index] != PRE_COMMENT_END) { // treat the comment as one char
					++index;
				}
			} else if (nodes[index] == POST_COMMENT_START) {
				while (nodes[index] != POST_COMMENT_END) { // treat the comment as one char
					++index;
				}
			} else if (isNagValue(nodes[index])) {
				nodes[index] = NO_MOVE;
				changed = true;
			}
		}
		return changed;
	}

	// ======================================================================

	boolean hasComment() {
		return hasComment;
	}

	private int skipPreComment(int index) {
		if (nodes[index] == PRE_COMMENT_START) {
			while (nodes[index] != PRE_COMMENT_END) {
				index++;
			}
		} else if (nodes[index] == PRE_COMMENT_END) {
			while (nodes[index] != PRE_COMMENT_START) {
				index--;
			}
		} else {
			throw new IllegalArgumentException(
					"No comment starts or ends at index " + index + " move " + valueToString(nodes[index]));
		}
		return index;
	}

	private int skipPostComment(int index) {
		if (nodes[index] == POST_COMMENT_START) {
			while (nodes[index] != POST_COMMENT_END) {
				index++;
			}
		} else if (nodes[index] == POST_COMMENT_END) {
			while (nodes[index] != POST_COMMENT_START) {
				index--;
			}
		} else {
			throw new IllegalArgumentException(
					"No comment starts or ends at index " + index + " move " + valueToString(nodes[index]));
		}
		return index;
	}

	String getPreMoveComment(int index) {
		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index]) && index != 0) {
				throw new IllegalArgumentException("No move at index " + index + " (value=" + valueToString(nodes[index]) + ")");
			}
		}
		if (index - 1 >= 0 && nodes[index - 1] == PRE_COMMENT_END) {
			index -= 2;
			StringBuilder sb = new StringBuilder();
			while (nodes[index] != PRE_COMMENT_START) {
				sb.insert(0, (char) nodes[index]);
				--index;
			}
			return sb.toString();
		} else if (index == 0) {
			index = 1;
			while (nodes[index] == NO_MOVE) {
				++index;
			}
			if (nodes[index] == PRE_COMMENT_START) {
				++index;
				StringBuilder sb = new StringBuilder();
				while (nodes[index] != PRE_COMMENT_END) {
					sb.append((char) nodes[index]);
					++index;
				}
				return sb.toString();
			}
		}
		return null;
	}

	String getPostMoveComment(int index) {
		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index]) && index != 0) {
				throw new IllegalArgumentException("No move at index " + index + " (value=" + valueToString(nodes[index]) + ")");
			}
		}

		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return null;
			}
		}

		// skip all nags and NO_MOVEs (which are deleted NAGs)
		while (isNagValue(nodes[index + 1]) || nodes[index + 1] == NO_MOVE) {
			index++;
		}

		if (nodes[index + 1] == POST_COMMENT_START) {
			index += 2;
			StringBuilder sb = new StringBuilder();
			while (nodes[index] != POST_COMMENT_END) {
				sb.append((char) nodes[index]);
				index++;
			}
			return sb.toString();
		} else {
			return null;
		}
	}

	/*
	 * Returns the index of the first move or -1, if no move exists.
	 */
	private int getFirstMoveIndex() {
		short move = nodes[1];
		if (move == LINE_END) { // No move, no comment
			return -1;
		}
		int index = 1;
		while (nodes[index] == NO_MOVE) {
			++index;
		}
		if (nodes[index] == PRE_COMMENT_START) {
			return skipPreComment(index) + 1;
		} else {
			return index;
		}
	}

	boolean setPreMoveComment(int index, String comment) {
		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}
		boolean remChange = removePreMoveComment(index);
		boolean addChange = addPreMoveComment(index, comment);
		if (remChange || addChange) {
			hasComment = true;
			return true;
		} else {
			return false;
		}
		// TN: that's not the same as
		// return removePreMoveComment(index) || addPreMoveComment(index, comment);
		// because addPreMoveComment is not executed, if removePreMoveComment returns true
	}

	boolean setPostMoveComment(int index, String comment) {
		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}
		boolean remChange = removePostMoveComment(index);
		boolean addChange = addPostMoveComment(index, comment);
		if (remChange || addChange) {
			hasComment = true;
			return true;
		} else {
			return false;
		}
		// TN: that's not the same as
		// return removePostMoveComment(index) || addPostMoveComment(index, comment);
		// because addPostMoveComment is not executed, if removePostMoveComment returns true
	}

	private boolean addPreMoveComment(int index, String comment) {
		if (DEBUG) {
			System.out.println("addPreMoveComment " + index + " comment " + comment);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index])) {
				throw new IllegalArgumentException("No move at index " + index + " (value=" + valueToString(nodes[index]) + ")");
			}
		}

		if (comment == null || comment.length() == 0) {
			return false;
		}

		makeSpace(index, comment.length() + 2, false);
		nodes[index] = PRE_COMMENT_START;
		for (int i = 0; i < comment.length(); i++) {
			short sh = (short) comment.charAt(i);
			if ((sh >= 32 && sh <= 126) || (sh >= 192 && sh <= 255)) {// these are the legal chars in PGN
				nodes[index + 1 + i] = sh;
			} else {
				nodes[index + 1 + i] = '?';
			}
		}
		nodes[index + comment.length() + 1] = PRE_COMMENT_END;
		changed();

		hasComment = true;

		if (DEBUG) {
			write(System.out);
		}
		return true;
	}

	private boolean addPostMoveComment(int index, String comment) {
		if (DEBUG) {
			System.out.println("addPostMoveComment " + index + " comment " + comment);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			if (index != 0 && !isMoveValue(nodes[index])) {
				throw new IllegalArgumentException("No move at index " + index + " (value=" + valueToString(nodes[index]) + ")");
			}
		}

		if (comment == null || comment.length() == 0) {
			return false;
		}

		// index = 0: comment before first move
		if (index != 0) {
			while (isNagValue(nodes[index + 1])) {
				index++;
			}
		}
		makeSpace(index + 1, comment.length() + 2, false);
		nodes[index + 1] = POST_COMMENT_START;
		for (int i = 0; i < comment.length(); i++) {
			short sh = (short) comment.charAt(i);
			if ((sh >= 32 && sh <= 126) || (sh >= 192 && sh <= 255)) {// these are the legal chars in PGN
				nodes[index + 2 + i] = sh;
			} else {
				nodes[index + 2 + i] = '?';
			}
		}
		nodes[index + comment.length() + 2] = POST_COMMENT_END;
		changed();

		hasComment = true;

		if (DEBUG) {
			write(System.out);
		}
		return true;
	}

	boolean removePreMoveComment(int index) {
		if (DEBUG) {
			System.out.println("removePreMoveComment " + index);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			if (!isMoveValue(nodes[index])) {
				throw new IllegalArgumentException("No move at index " + index + " (value=" + valueToString(nodes[index]) + ")");
			}
		}

		boolean isChanged = false;
		if (nodes[index - 1] == PRE_COMMENT_END) {
			for (int i = skipPreComment(index - 1); i < index; i++) {
				nodes[i] = NO_MOVE;
			}
			isChanged = true;
		}
		if (isChanged) {
			changed();
		}
		// TN added:
		if (isChanged) {
			hasComment = false;
			for (int i = 0; i < size; ++i) {
				if (nodes[i] == PRE_COMMENT_START || nodes[i] == POST_COMMENT_START) {
					hasComment = true;
					break;
				}
			}
		}

		if (DEBUG) {
			write(System.out);
		}
		return isChanged;
	}

	boolean removePostMoveComment(int index) {
		if (DEBUG) {
			System.out.println("removePostMoveComment " + index);
			write(System.out);
		}

		if (index == 0) {
			index = getFirstMoveIndex();
			if (index == -1) {
				return false;
			}
		}
		while (isNagValue(nodes[index + 1]) || (nodes[index + 1] == NO_MOVE)) {
			++index;
		}

		boolean isChanged = false;
		if (nodes[index + 1] == POST_COMMENT_START) {
			for (int i = skipPostComment(index + 1); i > index; i--) {
				nodes[i] = NO_MOVE;
			}
			isChanged = true;
		}
		if (isChanged) {
			changed();
			hasComment = false;
			for (int i = 0; i < size; ++i) {
				if (nodes[i] == PRE_COMMENT_START || nodes[i] == POST_COMMENT_START) {
					hasComment = true;
					break;
				}
			}
		}

		if (DEBUG) {
			write(System.out);
		}
		return isChanged;
	}

	void setEmptyGameComment(String comment) {
		if (comment != null && !comment.isEmpty()) {
			nodes = new short[Math.max(32, comment.length() + 4)];
			nodes[0] = LINE_START;
			nodes[1] = PRE_COMMENT_START;
			for (int i = 0; i < comment.length(); i++) {
				nodes[2 + i] = (short) comment.charAt(i);
			}
			nodes[comment.length() + 2] = PRE_COMMENT_END;
			nodes[comment.length() + 3] = LINE_END;
			size = comment.length() + 4;
			hasComment = true;
		} else {
			nodes = new short[32];
			nodes[0] = LINE_START;
			nodes[1] = LINE_END;
			size = 2;
			hashCode = 0;
			hasComment = false;
		}
	}

	String getEmptyGameComment() {
		if (getTotalNumOfPlies() == 0) {
			if (nodes[1] == PRE_COMMENT_START) {
				int index = 2;
				StringBuilder sb = new StringBuilder();
				while (nodes[index] != PRE_COMMENT_END) {
					sb.append((char) nodes[index]);
					++index;
				}
				return sb.toString();
			}
		}
		return null;
	}

	// ======================================================================

	boolean hasLines() {
		for (int i = 1; i < size; i++) {
			if (nodes[i] == LINE_START) {
				return true;
			}
		}
		return false;
	}

	int getTotalNumOfPlies() {
		boolean inComment = false;
		int num = 0;
		for (int i = 0; i < size; i++) {
			short move = nodes[i];
			if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				inComment = true;
				continue;
			}
			if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				inComment = false;
				continue;
			}
			if (!inComment && isMoveValue(move)) {
				++num;
			}
		}
		return num;
	}

	int getTotalCommentSize() {
		boolean inComment = false;
		int num = 0;
		for (int i = 0; i < size; i++) {
			short move = nodes[i];
			if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				inComment = false;
			}
			if (inComment) {
				num++;
			}
			if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				inComment = true;
			}
		}
		return num;
	}

	short getMove(int index) {
		if (index >= 0 && index < size) {
			short move = nodes[index];
			return (isMoveValue(move) ? move : NO_MOVE);
		} else {
			return NO_MOVE;
		}
	}

	/**
	 * Determines if the node at the given index belongs to the main line.
	 * 
	 * @return true, if the node at the given index belongs to the main line, false otherwise
	 */
	boolean isMainLine(int index) {
		int level = 0;
		for (int i = 0; i <= index; ++i) {
			short node = nodes[i];
			if (node == LINE_START) {
				++level;
			} else if (node == LINE_END) {
				--level;
			}
		}
		return level == 1;
	}

	/**
	 * @return -1 if at the beginning of a line
	 */
	int goBack(int index, boolean gotoMainLine) {
		if (DEBUG) {
			System.out.println("goBack " + index + " " + gotoMainLine);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		if (index <= 0) {
			return -1;
		}

		index--;
		int level = 0;
		while (index > 0) {
			short move = nodes[index];
			if (move == LINE_START) {
				level--;
				if (level == -1) {
					if (!gotoMainLine) {
						index = -1;
					} else {
						index = goBack(index, false); // now at main line's move
						index = goBack(index, false); // now one move back
					}
					break;
				}
			} else if (move == LINE_END) {
				level++;
			} else if (isNagValue(move)) {

			} else if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				// error
			} else if (move == PRE_COMMENT_END) {
				index = skipPreComment(index);
			} else if (move == POST_COMMENT_END) {
				index = skipPostComment(index);
			} else if (move == NO_MOVE) {

			} else if (level == 0) {
				break;
			}
			index--;
		}
		if (DEBUG) {
			System.out.println("  --> " + index);
		}
		return index;
	}

	/**
	 * Advances one move in the current line.
	 *
	 * @param index the index of the current move
	 *
	 * @return the index of the next move. If the next move does not exist, the
	 *         index points to a LINE_END, where a next move should be inserted.
	 */
	int goForward(int index) {
		if (DEBUG) {
			System.out.println("goForward " + index);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		index++;
		int level = 0;
		while (index < size - 1) {
			short move = nodes[index];
			if (move == LINE_START) {
				level++;
			} else if (move == LINE_END) {
				level--;
				if (level < 0) {
					break;
				}
			} else if (isNagValue(move)) {

			} else if (move == PRE_COMMENT_START) {
				index = skipPreComment(index);
			} else if (move == POST_COMMENT_START) {
				index = skipPostComment(index);
			} else if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				// error
			} else if (move == NO_MOVE) {

			} else if (level == 0) {
				break;
			}
			index++;
		}
		if (DEBUG) {
			System.out.println("  --> " + index);
		}
		return index;
	}

	int goForward(int index, int whichLine) {
		if (DEBUG) {
			System.out.println("goForward " + index + " " + whichLine);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		index = goForward(index);
		if (nodes[index] != LINE_END && whichLine > 0) {
			index++;
			int level = 0;
			while (index < size - 1) {
				short move = nodes[index];
				if (move == LINE_START) {
					level++;
					if (level == 1) {
						whichLine--;
					}
				} else if (move == LINE_END) {
					level--;
					if (level < 0) {
						break;
					}
				} else if (isNagValue(move)) {

				} else if (move == PRE_COMMENT_START) {
					index = skipPreComment(index);
				} else if (move == POST_COMMENT_START) {
					index = skipPostComment(index);
				} else if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
					// error
				} else if (move == NO_MOVE) {

				} else if (level == 1 && whichLine == 0) {
					break;
				} else if (level == 0) {
					index = -1;
					break;
				} // move on level 0 -> not enough lines
				index++;
			}
		}
		if (DEBUG) {
			System.out.println("  --> " + index);
		}
		return index;
	}

	int getNumOfNextMoves(int index) {
		if (DEBUG) {
			System.out.println("getNumOfNextMoves " + index);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		index = goForward(index);
		if (nodes[index] == LINE_END) {
			return 0;
		}

		index++;
		int numOfMoves = 1;
		int level = 0;
		while (index < size && level >= 0) {
			short move = nodes[index];
			if (move == LINE_START) {
				level++;
			} else if (move == LINE_END) {
				level--;
				if (level == 0) {
					numOfMoves++;
				}
			} else if (isNagValue(move)) {

			} else if (move == PRE_COMMENT_START) {
				index = skipPreComment(index);
			} else if (move == POST_COMMENT_START) {
				index = skipPostComment(index);
			} else if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				// error
			} else if (move == NO_MOVE) {

			} else if (level == 0) {
				break;
			}
			index++;
		}
		if (DEBUG) {
			System.out.println("  --> " + numOfMoves);
		}
		return numOfMoves;
	}

	boolean hasNextMove(int index) {
		if (DEBUG) {
			System.out.println("hasNextMove " + index);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		boolean nextMove = isMoveValue(nodes[goForward(index)]);
		if (DEBUG) {
			System.out.println("  --> " + nextMove);
		}
		return nextMove;
	}

	// ======================================================================

	private int findEarliestNoMove(int index) {
		while (index > 1 && nodes[index - 1] == NO_MOVE) {
			--index;
		}
		return index;
	}

	private int findLatestNoMove(int index) {
		while (index > 0 && nodes[index + 1] == NO_MOVE) {
			++index;
		}
		return index;
	}

	private void enlarge(int index, int addSize) {
		if (DEBUG) {
			System.out.println("enlarge " + index + " " + addSize);
			write(System.out);
		}

		short[] newNodes = new short[nodes.length + addSize];
		System.arraycopy(nodes, 0, newNodes, 0, index);
		System.arraycopy(nodes, index, newNodes, index + addSize, this.size - index);
		java.util.Arrays.fill(newNodes, index, index + addSize, NO_MOVE);
		nodes = newNodes;
		this.size += addSize;
		if (DEBUG) {
			write(System.out);
		}
	}

	private void makeSpace(int index, int spaceNeeded, boolean possiblyMakeMore) {
		if (DEBUG) {
			System.out.println("makeSpace " + index + " " + spaceNeeded);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			if (index < 1 || index >= size) {
				throw new IllegalArgumentException("Index out of bounds " + index + " (size=" + size + ")");
			}
		}

		for (int i = 0; i < spaceNeeded; i++) {
			if (nodes[index + i] != NO_MOVE) {
				// not enough space, make it
				if (size + spaceNeeded - i >= nodes.length) {
					int size = (spaceNeeded - i < 8 && possiblyMakeMore ? 8 : spaceNeeded - i);
					enlarge(index, size);
				} else {
					System.arraycopy(nodes, index + i, nodes, index + spaceNeeded, size - (index + i));
					java.util.Arrays.fill(nodes, index + i, index + spaceNeeded, NO_MOVE);
					size += spaceNeeded - i;
				}
				break;
			}
		}
		if (DEBUG) {
			write(System.out);
		}
	}

	int appendAsRightMostLine(int index, short move) {
		if (DEBUG) {
			System.out.println("appendAsRightMostLine " + index + " " + Move.getString(move));
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		if (hasNextMove(index)) { // move opens a new line
			index = goForward(index); // go to the move for which an alternative is entered
			index = goForward(index); // go to the following move or to the line's end
			if (nodes[index - 1] == PRE_COMMENT_END) { // go before of a pre move comment
				while (nodes[index - 1] != PRE_COMMENT_START) {
					--index;
				}
				--index;
			}
			index = findEarliestNoMove(index); // go to left border of a no move zone

			makeSpace(index, 3, true);
			nodes[index] = LINE_START;
			nodes[index + 1] = move;
			nodes[findLatestNoMove(index + 2)] = LINE_END; // set to the right border of no move zone
			if (DEBUG) {
				write(System.out);
			}
			if (DEBUG) {
				System.out.println("  --> " + index);
			}
			changed();
			return index + 1;
		} else { // append a move to current line
			index = goForward(index); // go to line's end
			index = findEarliestNoMove(index); // go to left border of a no move zone
			makeSpace(index, 1, true);
			nodes[index] = move;
			if (DEBUG) {
				write(System.out);
			}
			if (DEBUG) {
				System.out.println("  --> " + index);
			}
			changed();
			return index;
		}
	}

	// The return value is the new index of the current move or -1 if something failed.
	int promoteVariation(int index) {
		if (DEBUG) {
			System.out.println("promoteVariation " + index);
			write(System.out);
		}
		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		VariationsModel varModel = new VariationsModel(nodes, size);
		Map.Entry<Integer, Integer> upVar = varModel.getVariation(index);
		List<Integer> siblings = varModel.getSiblings(upVar.getKey());
		Map.Entry<Integer, Integer> downVar = varModel.getVariation(siblings.get(0) - 1);

		// for the return value
		int moveNumberInVariation = 0;
		int k = index;
		int level = 0;
		while (level >= 0) {
			if (!Move.isSpecial(nodes[k])) {
				++moveNumberInVariation;
			} else if (nodes[k] == LINE_START) {
				--level;
			} else if (nodes[k] == LINE_END) {
				++level;
			}
			--k;

		}

		short[] newMoves = new short[nodes.length];
		int retVal = -1;
		// copy everything from the beginning that remains unchanged
		int copyIndex = siblings.get(0);
		while (Move.isSpecial(nodes[copyIndex])) {
			--copyIndex;
		}
		if (nodes[copyIndex - 1] == PRE_COMMENT_END) {
			copyIndex -= 2;
			while (nodes[copyIndex] != PRE_COMMENT_START) {
				--copyIndex;
			}
		}
		System.arraycopy(nodes, 0, newMoves, 0, copyIndex);
		int copied = copyIndex;

		// copy the first move of the promoted line
		int startIndex = upVar.getKey() + 1;
		int endIndex = startIndex;
		while (Move.isSpecial(nodes[endIndex])) {
			++endIndex;
		}
		while (isNagValue(nodes[endIndex + 1])) {
			++endIndex;
		}
		if (nodes[endIndex + 1] == POST_COMMENT_START) {
			endIndex += 2;
			while (nodes[endIndex] != POST_COMMENT_END) {
				++endIndex;
			}
		}
		int length1 = endIndex - startIndex + 1;
		System.arraycopy(nodes, startIndex, newMoves, copied, length1);
		if (moveNumberInVariation == 1) {
			for (int l = copied; l < copied + length1; ++l) {
				if (!Move.isSpecial(newMoves[l])) {
					retVal = l;
				}
			}
		}
		copied += endIndex - startIndex + 1;

		// copy the first move of the down-graded line
		newMoves[copied] = LINE_START;
		++copied;
		System.arraycopy(nodes, copyIndex, newMoves, copied, siblings.get(0) - copyIndex);
		copied += siblings.get(0) - copyIndex;

		// copy the rest of the down-graded line
		int lastSiblingIndex = varModel.getVariation(siblings.get(siblings.size() - 1)).getValue() + 1;
		int lastDownGradedLine = downVar.getValue();
		System.arraycopy(nodes, lastSiblingIndex, newMoves, copied, lastDownGradedLine - lastSiblingIndex + 1);
		copied += lastDownGradedLine - lastSiblingIndex + 1;

		// copy the siblings before the promoted line
		for (Integer siblingStart : siblings) {
			if (siblingStart < upVar.getKey()) { // predecessor
				int seblingEnd = varModel.getVariation(siblingStart).getValue();
				int length = seblingEnd - siblingStart + 1;
				System.arraycopy(nodes, siblingStart, newMoves, copied, length);
				copied += length;
			} else {
				break;
			}
		}

		// copy the siblings after the promoted line
		for (int i = siblings.size() - 1; i >= 0; --i) {
			int siblingStart = siblings.get(i);
			if (siblingStart > upVar.getKey()) { // successor
				int seblingEnd = varModel.getVariation(siblingStart).getValue();
				int length = seblingEnd - siblingStart + 1;
				System.arraycopy(nodes, siblingStart, newMoves, copied, length);
				copied += length;
			} else {
				break;
			}
		}

		// copy the rest of the promoted line
		int firstRestFromPromoted = endIndex + 1;
		int lastRestFromPromoted = upVar.getValue();
		int length = lastRestFromPromoted - firstRestFromPromoted + 1;
		System.arraycopy(nodes, firstRestFromPromoted, newMoves, copied, length);
		if (moveNumberInVariation > 1) {
			int counter = 1;
			for (int j = copied; j < copied + length; ++j) {
				if (!Move.isSpecial(newMoves[j])) {
					++counter;
					if (counter >= moveNumberInVariation) {
						retVal = j;
						break;
					}
				}
			}
		}
		copied += length;

		// copy everything after the changed lines
		System.arraycopy(nodes, copied, newMoves, copied, size - copied);

		if (retVal >= 0) {
			nodes = newMoves;
		}
		return retVal;
	}

	// The return value indicates, whether some move was deleted.
	boolean deleteRemainingMoves(int index) {
		if (DEBUG) {
			System.out.println("deleteRemainingMoves " + index);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		// go to the next move
		index = goForward(index);
		if (nodes[index] == LINE_END) {
			return false;
		}

		int level = 0;
		boolean deleteLineEnd = false;

		boolean inComment = false;
		while (index < size) {
			short move = nodes[index];
			if (!inComment && move == LINE_START) {
				level++;
			} else if (!inComment && move == LINE_END) {
				level--;
			} else if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				inComment = true;
			} else if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				inComment = false;
			}
			if (level == -1) {
				if (deleteLineEnd) {
					nodes[index] = NO_MOVE;
				}
				break;
			}
			nodes[index] = NO_MOVE;
			index++;
		}
		changed();
		if (DEBUG) {
			write(System.out);
		}
		return true;
	}

	void deleteCurrentLine(int index) {
		if (DEBUG) {
			System.out.println("deleteCurrentLine " + index);
			write(System.out);
		}

		if (EXTRA_CHECKS) {
			checkLegalCursor(index);
		}

		int level = 0;
		boolean deleteLineEnd = false;

		// TN: the old version assumed to be on a LINE_START already, because this method was 
		// called only when a Game::goBackToLineBegin was called.
		// In general, this assumption is wrong, and this became evident, when pre-move
		// comments where introduced.		
		// Go back to next LINE_START:
		for (int i = index - 1; i >= 0; --i) {
			short move = nodes[i];
			if (move == LINE_START) {
				index = i;
				deleteLineEnd = true;
				level = -1;
				break;
			}
		}

		boolean inComment = false;
		while (index < size) {
			short move = nodes[index];
			if (!inComment && move == LINE_START) {
				level++;
			} else if (!inComment && move == LINE_END) {
				level--;
			} else if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				inComment = true;
			} else if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				inComment = false;
			}
			if (level == -1) {
				if (deleteLineEnd) {
					nodes[index] = NO_MOVE;
				}
				break;
			}
			nodes[index] = NO_MOVE;
			index++;
		}
		changed();
		if (DEBUG) {
			write(System.out);
		}
	}

	// ======================================================================

	boolean deleteAllSublines() {
		if (DEBUG) {
			System.out.println("deleteAllLines");
			write(System.out);
		}

		boolean changed = false;
		int level = -1; // 0 is mainline
		for (int index = 0; index < size; ++index) {
			short move = nodes[index];
			if (move == LINE_START) {
				++level;
			}
			if (level > 0) {
				nodes[index] = NO_MOVE;
				changed = true;
			}
			if (move == LINE_END) {
				--level;
			}
		}
		if (changed) {
			changed();
		}
		if (DEBUG) {
			write(System.out);
		}
		return changed;
	}

	// ======================================================================

	int pack(int index) {
		if (DEBUG) {
			System.out.println("pack");
			write(System.out);
		}

		int newSize = 0;
		for (int i = 0; i < size; i++) {
			if (nodes[i] != NO_MOVE) {
				newSize++;
			}
		}

		short[] newMoves = new short[newSize + 1];
		int j = 0;
		boolean inComment = false;
		for (int i = 0; i < size; i++) {
			short move = nodes[i];
			if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				inComment = true;
			} else if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				inComment = false;
			}
			if (inComment || (move != NO_MOVE)) {
				newMoves[j++] = move;
			}
			if (i == index) {
				index = j - 1;
			}
		}

		nodes = newMoves;
		nodes[newSize] = LINE_END;
		size = newSize;

		if (DEBUG) {
			write(System.out);
		}
		if (DEBUG) {
			System.out.println("  --> " + index);
		}

		return index;
	}

	// ======================================================================

	static String valueToString(short value) {
		if (value == LINE_START) {
			return "(";
		} else if (value == LINE_END) {
			return ")";
		} else if (value == NO_MOVE) {
			return "NO";
		} else if (value == PRE_COMMENT_START || value == POST_COMMENT_START) {
			return "{";
		} else if (value == PRE_COMMENT_END || value == POST_COMMENT_END) {
			return "}";
		} else if (isNagValue(value)) {
			return "$" + getNagForValue(value);
		} else {
			return Move.getString(value);
		}
	}

	void write(PrintStream out) {
		boolean inComment = false;
		for (int i = 0; i < size; i++) {
			short move = nodes[i];
			if (move == PRE_COMMENT_END || move == POST_COMMENT_END) {
				inComment = false;
			}
			if (inComment) {
				out.print((char) move);
			} else {
				out.print(valueToString(nodes[i]));
				out.print(" ");
			}
			if ((i % 20) == 19) {
				out.println();
			}
			if (move == PRE_COMMENT_START || move == POST_COMMENT_START) {
				inComment = true;
			}
		}
		out.println();
	}

	// ======================================================================

	long getHashCode() {
		if (hashCode == 0) {
			int shift = 0;
			for (int index = 0;; index = goForward(index)) {
				if (nodes[index] == LINE_END) {
					break;
				}
				short move = getMove(index);
				hashCode ^= (long) move << shift;
				if (shift == 12) {
					shift = 0;
				} else {
					shift++;
				}
			}
		}
		return hashCode;
	}

	@Override
	public int hashCode() {
		return (int) getHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof GameMoveModel gameMoveModel) || (gameMoveModel.getHashCode() != getHashCode())) {
			return false;
		}

		int index1 = 0, index2 = 0;
		for (;;) {
			short move1 = nodes[index1];
			short move2 = gameMoveModel.nodes[index2];
			if (move1 == LINE_END && move2 == LINE_END) {
				return true;
			}
			if (move1 != move2) {
				return false;
			}
			index1 = goForward(index1);
			index2 = gameMoveModel.goForward(index2);
		}
	}

	@Override
	public int compareTo(GameMoveModel other) {
		int index1 = 0, index2 = 0;
		for (;;) {
			short move1 = nodes[index1];
			short move2 = other.nodes[index2];
			if (move1 == LINE_END && move2 == LINE_END) {
				return 0;
			}
			if (move1 != move2) {
				return move1 - move2;
			}
			index1 = goForward(index1);
			index2 = other.goForward(index2);
		}
	}
}
