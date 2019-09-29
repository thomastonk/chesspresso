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
package chesspresso.game;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
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
 * @version $Revision: 1.2 $
 */
public class GameMoveModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private final static boolean DEBUG = false;
    private final static boolean EXTRA_CHECKS = true;

    // ======================================================================

    public final static int MODE_EVERYTHING = 0;

    final static short NO_MOVE = (short) Move.NO_MOVE, LINE_START = (short) Move.OTHER_SPECIALS,
	    LINE_END = (short) Move.OTHER_SPECIALS + 1, PRE_COMMENT_START = (short) Move.OTHER_SPECIALS + 2,
	    PRE_COMMENT_END = (short) Move.OTHER_SPECIALS + 3, POST_COMMENT_START = (short) Move.OTHER_SPECIALS + 4,
	    POST_COMMENT_END = (short) Move.OTHER_SPECIALS + 5, NAG_BASE = (short) Move.OTHER_SPECIALS + 16,
	    LAST_SPECIAL = (short) (NAG_BASE + NAG.NUM_OF_NAGS);

    static {
	if (LAST_SPECIAL > Move.SPECIAL_MOVE + Move.NUM_OF_SPECIAL_MOVES) {
	    throw new RuntimeException("Not enough space to define special moves for game move model");
	}
    }

    // ======================================================================

    private short[] m_moves; // TN: a better name would be m_nodes!
    private int m_size;
    private int m_hashCode;
    // TN added:
    private boolean m_hasComment;

    // ======================================================================

    public GameMoveModel() {
	m_moves = new short[32];
	m_moves[0] = LINE_START;
	m_moves[1] = LINE_END;
	m_size = 2;
	m_hashCode = 0;
	m_hasComment = false;
    }

    public GameMoveModel(DataInput in, int mode) throws IOException {
	load(in, mode);
	m_hashCode = 0; // store in file?
    }

    // TN added:

    public void clear() {
	for (int index = 0; index < m_moves.length; ++index) {
	    m_moves[index] = 0;
	}
	m_moves[0] = LINE_START;
	m_moves[1] = LINE_END;
	m_size = 2;
	m_hashCode = 0;
	m_hasComment = false;
    }

    // TN added:
    public GameMoveModel getDeepCopy() {
	GameMoveModel copy = new GameMoveModel();
	copy.m_moves = new short[this.m_moves.length];
	System.arraycopy(this.m_moves, 0, copy.m_moves, 0, copy.m_moves.length);
	copy.m_size = this.m_size;
	copy.m_hashCode = 0;
	copy.m_hasComment = m_hasComment;
	return copy;
    }

    // ======================================================================
    // invariant checking

    private void checkLegalCursor(int index) {
	if (index < 0)
	    throw new RuntimeException("Illegal index " + index);
	if (index >= m_size)
	    throw new RuntimeException("Illegal index " + index + " m_size=" + m_size);
	if (m_moves[index] != LINE_START && !isMoveValue(m_moves[index]))
	    throw new RuntimeException("No move at index " + index + " (value=" + valueToString(m_moves[index]) + ")");
    }

    // ======================================================================

    private static boolean isMoveValue(short value) {
	return !Move.isSpecial(value);
    }
    // private static boolean isSpecialValue(short value) {return
    // Move.isSpecial(value);}

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
	m_hashCode = 0;
    }

    // ======================================================================

    public boolean hasNag(int index, short nag) {
	if (DEBUG) {
	    System.out.println("hasNag " + index + " nag " + nag);
	    write(System.out);
	}

	short nagValue = getValueForNag(nag);
	short value;
	do {
	    index++;
	    value = m_moves[index];
	    if (value == nagValue)
		return true;
	} while (isNagValue(value));

	return false;
    }

    public short[] getNags(int index) {
	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " move=" + valueToString(m_moves[index]));

	int num = 0;
	while (isNagValue(m_moves[index + 1])) {
	    index++;
	    num++;
	}
	if (num == 0) {
	    return null;
	} else {
	    short[] nags = new short[num];
	    // collect nags from back to front (most recently added last)
	    for (int i = 0; i < num; i++)
		nags[i] = getNagForValue(m_moves[index - i]);
	    Arrays.sort(nags); // TN: added this line to get !,? etc before =, +- etc
	    return nags;
	}
    }

    public void addNag(int index, short nag) {
	if (DEBUG) {
	    System.out.println("addNag " + index + " nag " + nag);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " val=" + valueToString(m_moves[index]));

	makeSpace(index + 1, 1, false); // most recent nag first
	m_moves[index + 1] = getValueForNag(nag);
	changed();

	if (DEBUG)
	    write(System.out);
    }

    public boolean removeNag(int index, short nag) {
	if (DEBUG) {
	    System.out.println("removeNag " + index + " nag " + nag);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " val=" + valueToString(m_moves[index]));

	short nagValue = getValueForNag(nag);
	short value;
	boolean changed = false;
	do {
	    index++;
	    value = m_moves[index];
	    if (value == nagValue) {
		while (isNagValue(m_moves[index + 1])) {
		    m_moves[index] = m_moves[index + 1];
		    index++;
		}
		m_moves[index] = NO_MOVE;
		changed = true;
		break;
	    }
	} while (isNagValue(value));
	changed();

	if (DEBUG)
	    write(System.out);
	return changed;
    }

    // TN added:
    public boolean removePunctuationNags(int index) {
	boolean changed = false;
	for (short nag = NAG.PUNCTUATION_NAG_BEGIN; nag <= NAG.PUNCTUATION_NAG_END; ++nag) {
	    if (removeNag(index, nag)) {
		changed = true;
	    }
	}
	return changed;
    }

    // TN added:
    public boolean removeEvaluationNags(int index) {
	boolean changed = false;
	for (short nag = NAG.EVALUATION_NAG_BEGIN; nag <= NAG.EVALUATION_NAG_END; ++nag) {
	    if (removeNag(index, nag)) {
		changed = true;
	    }
	}
	return changed;
    }

    // TN added:
    public boolean removeAllNags() {
	boolean changed = false;
	for (int index = 0; index < m_size; ++index) {
	    if (isNagValue(m_moves[index])) {
		m_moves[index] = NO_MOVE;
		changed = true;
	    }
	}
	return changed;
    }

    // ======================================================================

    public boolean hasComment() {
	return m_hasComment;
    }

    private int skipPreComment(int index) {
	if (m_moves[index] == PRE_COMMENT_START) {
	    while (m_moves[index] != PRE_COMMENT_END)
		index++;
	} else if (m_moves[index] == PRE_COMMENT_END) {
	    while (m_moves[index] != PRE_COMMENT_START)
		index--;
	} else {
	    throw new RuntimeException(
		    "No comment start or end at index " + index + " move " + valueToString(m_moves[index]));
	}
	return index;
    }

    private int skipPostComment(int index) {
	if (m_moves[index] == POST_COMMENT_START) {
	    while (m_moves[index] != POST_COMMENT_END)
		index++;
	} else if (m_moves[index] == POST_COMMENT_END) {
	    while (m_moves[index] != POST_COMMENT_START)
		index--;
	} else {
	    throw new RuntimeException(
		    "No comment start or end at index " + index + " move " + valueToString(m_moves[index]));
	}
	return index;
    }

    public String getPreMoveComment(int index) {
	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]) && index != 0)
		throw new RuntimeException("No move at index " + index + " move=" + valueToString(m_moves[index]));
	if (index - 1 >= 0 && m_moves[index - 1] == PRE_COMMENT_END) {
	    index -= 2;
	    StringBuffer sb = new StringBuffer();
	    while (m_moves[index] != PRE_COMMENT_START) {
		sb.insert(0, (char) m_moves[index]);
		--index;
	    }
	    return sb.toString();
	} else {
	    return null;
	}
    }

    public String getPostMoveComment(int index) {
	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]) && index != 0)
		throw new RuntimeException("No move at index " + index + " move=" + valueToString(m_moves[index]));

	// skip all nags
	while (isNagValue(m_moves[index + 1]))
	    index++;

	if (m_moves[index + 1] == POST_COMMENT_START) {
	    index += 2;
	    StringBuffer sb = new StringBuffer();
	    while (m_moves[index] != POST_COMMENT_END) {
		sb.append((char) m_moves[index]);
		index++;
	    }
	    return sb.toString();
	} else {
	    return null;
	}
    }

    public boolean setPreMoveComment(int index, String comment) {
	boolean remChange = removePreMoveComment(index);
	boolean addChange = addPreMoveComment(index, comment);
	if (remChange || addChange) {
	    m_hasComment = true;
	    return true;
	} else {
	    return false;
	}
	// TN trap: that's not the the same as
	// return removePreMoveComment(index) || addPreMoveComment(index, comment);
	// because addPreMoveComment is not executed, if removePreMoveComment returns
	// true
    }

    public boolean setPostMoveComment(int index, String comment) {
	boolean remChange = removePostMoveComment(index);
	boolean addChange = addPostMoveComment(index, comment);
	if (remChange || addChange) {
	    m_hasComment = true;
	    return true;
	} else {
	    return false;
	}
	// TN trap: that's not the the same as
	// return removePostMoveComment(index) || addPostMoveComment(index, comment);
	// because addPostMoveComment is not executed, if removePostMoveComment returns
	// true
    }

    private boolean addPreMoveComment(int index, String comment) {
	if (DEBUG) {
	    System.out.println("addPreMoveComment " + index + " comment " + comment);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " val=" + valueToString(m_moves[index]));

	if (comment == null || comment.length() == 0)
	    return false; // =====>

	makeSpace(index, comment.length() + 2, false);
	m_moves[index] = PRE_COMMENT_START;
	for (int i = 0; i < comment.length(); i++) {
	    m_moves[index + 1 + i] = (short) comment.charAt(i);
	}
	m_moves[index + comment.length() + 1] = PRE_COMMENT_END;
	changed();

	m_hasComment = true;

	if (DEBUG)
	    write(System.out);
	return true;
    }

    private boolean addPostMoveComment(int index, String comment) {
	if (DEBUG) {
	    System.out.println("addPostMoveComment " + index + " comment " + comment);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (index != 0 && !isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " val=" + valueToString(m_moves[index]));

	if (comment == null || comment.length() == 0)
	    return false; // =====>

	// index = 0: comment before first move
	if (index != 0) {
	    while (isNagValue(m_moves[index + 1]))
		index++;
	}
	makeSpace(index + 1, comment.length() + 2, false);
	m_moves[index + 1] = POST_COMMENT_START;
	for (int i = 0; i < comment.length(); i++) {
	    m_moves[index + 2 + i] = (short) comment.charAt(i);
	}
	m_moves[index + comment.length() + 2] = POST_COMMENT_END;
	changed();

	m_hasComment = true;

	if (DEBUG)
	    write(System.out);
	return true;
    }

    public boolean removePreMoveComment(int index) {
	if (DEBUG) {
	    System.out.println("removePreMoveComment " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (!isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " val=" + valueToString(m_moves[index]));

	boolean isChanged = false;
	if (m_moves[index - 1] == PRE_COMMENT_END) {
	    for (int i = skipPreComment(index - 1); i < index; i++) {
		m_moves[i] = NO_MOVE;
	    }
	    isChanged = true;
	}
	if (isChanged)
	    changed();
	// TN added:
	if (isChanged) {
	    m_hasComment = false;
	    for (int i = 0; i < m_size; ++i) {
		if (m_moves[i] == PRE_COMMENT_START || m_moves[i] == POST_COMMENT_START) {
		    m_hasComment = true;
		    break;
		}
	    }
	}

	if (DEBUG)
	    write(System.out);
	return isChanged;
    }

    public boolean removePostMoveComment(int index) {
	if (DEBUG) {
	    System.out.println("removePostMoveComment " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (index != 0 && !isMoveValue(m_moves[index]))
		throw new RuntimeException("No move at index " + index + " val=" + valueToString(m_moves[index]));

	// allow comments before first move (index == 0)
	if (index != 0) {
	    while (isNagValue(m_moves[index + 1]))
		index++;
	}
	boolean isChanged = false;
	if (m_moves[index + 1] == POST_COMMENT_START) {
	    for (int i = skipPostComment(index + 1); i > index; i--) {
		m_moves[i] = NO_MOVE;
	    }
	    isChanged = true;
	}
	if (isChanged)
	    changed();
	// TN added:
	if (isChanged) {
	    m_hasComment = false;
	    for (int i = 0; i < m_size; ++i) {
		if (m_moves[i] == PRE_COMMENT_START || m_moves[i] == POST_COMMENT_START) {
		    m_hasComment = true;
		    break;
		}
	    }
	}

	if (DEBUG)
	    write(System.out);
	return isChanged;
    }

    public void setEmptyGameComment(String comment) {
	if (comment != null && !comment.isEmpty()) {
	    m_moves = new short[Math.max(32, comment.length() + 4)];
	    m_moves[0] = LINE_START;
	    m_moves[1] = PRE_COMMENT_START;
	    for (int i = 0; i < comment.length(); i++) {
		m_moves[2 + i] = (short) comment.charAt(i);
	    }
	    m_moves[comment.length() + 2] = PRE_COMMENT_END;
	    m_moves[comment.length() + 3] = LINE_END;
	    m_size = comment.length() + 4;
	    m_hasComment = true;
	} else {
	    m_moves = new short[32];
	    m_moves[0] = LINE_START;
	    m_moves[1] = LINE_END;
	    m_size = 2;
	    m_hashCode = 0;
	    m_hasComment = false;
	}
    }

    public String getEmptyGameComment() {
	if (getTotalNumOfPlies() == 0) {
	    if (m_moves[1] == PRE_COMMENT_START) {
		int index = 2;
		StringBuffer sb = new StringBuffer();
		while (m_moves[index] != PRE_COMMENT_END) {
		    sb.append((char) m_moves[index]);
		    ++index;
		}
		return sb.toString();
	    }
	}
	return null;
    }

    // ======================================================================

    public boolean hasLines() {
	for (int i = 1; i < m_size; i++) {
	    if (m_moves[i] == LINE_START)
		return true;
	}
	return false;
    }

    public int getTotalNumOfPlies() {
	int num = 0;
	for (int index = 0; index < m_size; index++) {
	    if (isMoveValue(m_moves[index]))
		num++;
	}
	return num;
    }

    public int getTotalCommentSize() {
	boolean inComment = false;
	int num = 0;
	for (int i = 0; i < m_size; i++) {
	    short move = m_moves[i];
	    if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		inComment = false;
	    if (inComment)
		num++;
	    if (move == PRE_COMMENT_START || move == POST_COMMENT_START)
		inComment = true;
	}
	return num;
    }

    public short getMove(int index) {
	if (index >= 0 && index < m_size) {
	    short move = m_moves[index];
	    return (isMoveValue(move) ? move : NO_MOVE);
	} else {
	    return NO_MOVE;
	}
    }

    // public int goBackToMainLine(int index)
    // {
    // if (DEBUG) {
    // System.out.println("goBackToMainLine " + index);
    // write(System.out);
    // }
    //
    // index--;
    // int level = 1;
    // while (index > 0) {
    // short move = m_moves[index];
    // if (move == LINE_START) level--;
    // else if (move == LINE_END) level++;
    // else if (isNagValue(move)) ;
    // else if (move == COMMENT_START) ; // error
    // else if (move == COMMENT_END) index = skipComment(index);
    // else if (move == NO_MOVE) ;
    // else if (level == 0) break;
    // index--;
    // }
    // if (DEBUG) System.out.println(" --> " + index);
    // return index;
    // }

    // TN added:
    /**
     * Determines if current position is the main line.
     * 
     * @return true, if main line
     */
    public boolean isMainLine(int index) {
	int level = 0;
	while (index >= 0) {
	    short move = m_moves[index];
	    if (move == LINE_START) {
		++level;
		if (level == 1) {
		    return index == 0;
		}
	    } else if (move == LINE_END) {
		--level;
	    }
	    --index;
	}
	return true;
    }

    /**
     * @return -1 if at the beginning of a line
     */
    public int goBack(int index, boolean gotoMainLine) {
	if (DEBUG) {
	    System.out.println("goBack " + index + " " + gotoMainLine);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	if (index <= 0)
	    return -1;

	index--;
	int level = 0;
	while (index > 0) {
	    short move = m_moves[index];
	    if (move == LINE_START) {
		level--;
		if (level == -1) {
		    if (!gotoMainLine) {
			index = -1;
			break;
		    } else {
			index = goBack(index, false); // now at main line's move
			index = goBack(index, false); // now one move back
			break;
		    }
		}
	    } else if (move == LINE_END)
		level++;
	    else if (isNagValue(move))
		;
	    else if (move == PRE_COMMENT_START || move == POST_COMMENT_START)
		; // error
	    else if (move == PRE_COMMENT_END)
		index = skipPreComment(index);
	    else if (move == POST_COMMENT_END)
		index = skipPostComment(index);
	    else if (move == NO_MOVE)
		;
	    else if (level == 0)
		break; // =====>
	    // else if (level < 0) {
	    // if (gotoMainLine) {
	    // return goBack(index, false); // =====>
	    // } else {
	    // index = -1; break;
	    // }
	    // }
	    index--;
	}
	if (DEBUG)
	    System.out.println("  --> " + index);
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
    public int goForward(int index) {
	if (DEBUG) {
	    System.out.println("goForward " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	// if (index >= 0 && m_moves[index] == LINE_END) return index; // =====>
	// if (index >= m_size - 1) return index; // =====>

	index++;
	int level = 0;
	while (index < m_size - 1) {
	    short move = m_moves[index];
	    if (move == LINE_START)
		level++;
	    else if (move == LINE_END) {
		level--;
		if (level < 0)
		    break;
	    } else if (isNagValue(move))
		;
	    else if (move == PRE_COMMENT_START)
		index = skipPreComment(index);
	    else if (move == POST_COMMENT_START)
		index = skipPostComment(index);
	    else if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		; // error
	    else if (move == NO_MOVE)
		;
	    else if (level == 0)
		break;
	    index++;
	}
	if (DEBUG)
	    System.out.println("  --> " + index);
	return index;
    }

    public int goForward(int index, int whichLine) {
	if (DEBUG) {
	    System.out.println("goForward " + index + " " + whichLine);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	index = goForward(index);
	if (m_moves[index] != LINE_END && whichLine > 0) {
	    index++;
	    int level = 0;
	    while (index < m_size - 1) {
		short move = m_moves[index];
		if (move == LINE_START) {
		    level++;
		    if (level == 1)
			whichLine--;
		} else if (move == LINE_END) {
		    level--;
		    if (level < 0)
			break;
		} else if (isNagValue(move))
		    ;
		else if (move == PRE_COMMENT_START)
		    index = skipPreComment(index);
		else if (move == POST_COMMENT_START)
		    index = skipPostComment(index);
		else if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		    ; // error
		else if (move == NO_MOVE)
		    ;
		else if (level == 1 && whichLine == 0)
		    break;
		else if (level == 0) {
		    index = -1;
		    break;
		} // =====> move on level 0 -> not enough lines
		index++;
	    }
	}
	if (DEBUG)
	    System.out.println("  --> " + index);
	return index;
    }

    public int getNumOfNextMoves(int index) {
	if (DEBUG) {
	    System.out.println("getNumOfNextMoves " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	index = goForward(index);
	if (m_moves[index] == LINE_END)
	    return 0; // =====>

	index++;
	int numOfMoves = 1;
	int level = 0;
	while (index < m_size && level >= 0) {
	    short move = m_moves[index];
	    if (move == LINE_START)
		level++;
	    else if (move == LINE_END) {
		level--;
		if (level == 0)
		    numOfMoves++;
	    } else if (isNagValue(move))
		;
	    else if (move == PRE_COMMENT_START)
		index = skipPreComment(index);
	    else if (move == POST_COMMENT_START)
		index = skipPostComment(index);
	    else if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		; // error
	    else if (move == NO_MOVE)
		;
	    else if (level == 0)
		break;
	    index++;
	}
	if (DEBUG)
	    System.out.println("  --> " + numOfMoves);
	return numOfMoves;
    }

    public boolean hasNextMove(int index) {
	if (DEBUG) {
	    System.out.println("hasNextMove " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	boolean nextMove = isMoveValue(m_moves[goForward(index)]);
	if (DEBUG)
	    System.out.println("  --> " + nextMove);
	return (nextMove);
    }

    // ======================================================================

    private int findEarliestNoMove(int index) {
	while (index > 1 && m_moves[index - 1] == NO_MOVE)
	    index--;
	return index;
    }

    private int findLatestNoMove(int index) {
	if (EXTRA_CHECKS)
	    if (index < 1 || index > m_size)
		throw new RuntimeException("Index out of bounds " + index);
	    else if (m_moves[index] != NO_MOVE)
		throw new RuntimeException("Expected no move  " + index);

	while (index > 0 && m_moves[index - 1] == NO_MOVE)
	    index--;
	return index;
    }

    private void enlarge(int index, int size) {
	if (DEBUG) {
	    System.out.println("enlarge " + index + " " + size);
	    write(System.out);
	}

	short[] newMoves = new short[m_moves.length + size];
	System.arraycopy(m_moves, 0, newMoves, 0, index);
	System.arraycopy(m_moves, index, newMoves, index + size, m_size - index);
	java.util.Arrays.fill(newMoves, index, index + size, NO_MOVE);
	m_moves = newMoves;
	m_size += size;
	if (DEBUG)
	    write(System.out);
    }

    private void makeSpace(int index, int spaceNeeded, boolean possiblyMakeMore) {
	if (DEBUG) {
	    System.out.println("makeSpace " + index + " " + spaceNeeded);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    if (index < 1 || index >= m_size)
		throw new RuntimeException("Index out of bounds " + index + " size=" + m_size);

	for (int i = 0; i < spaceNeeded; i++) {
	    if (m_moves[index + i] != NO_MOVE) {
		// not enough space, make it
		if (m_size + spaceNeeded - i >= m_moves.length) {
		    int size = (spaceNeeded - i < 8 && possiblyMakeMore ? 8 : spaceNeeded - i);
		    enlarge(index, size);
		} else {
		    System.arraycopy(m_moves, index + i, m_moves, index + spaceNeeded, m_size - (index + i));
		    java.util.Arrays.fill(m_moves, index + i, index + spaceNeeded, NO_MOVE);
		    m_size += spaceNeeded - i;
		}
		break;
	    }
	}
	if (DEBUG)
	    write(System.out);
    }

    public int appendAsRightMostLine(int index, short move) {
	if (DEBUG) {
	    System.out.println("appendAsRightMostLine " + index + " " + Move.getString(move));
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	if (hasNextMove(index)) {
	    index = goForward(index); // go to the move for which an alternative
				      // is entered
	    index = goForward(index); // go to the end of all existing lines
	    index = findEarliestNoMove(index);
	    makeSpace(index, 3, true);
	    m_moves[index] = LINE_START;
	    m_moves[index + 1] = move;
	    m_moves[findLatestNoMove(index + 2)] = LINE_END;
	    if (DEBUG)
		write(System.out);
	    if (DEBUG)
		System.out.println("  --> " + index);
	    changed();
	    return index + 1;
	} else {
	    index = goForward(index);
	    index = findEarliestNoMove(index);
	    makeSpace(index, 1, true);
	    m_moves[index] = move;
	    if (DEBUG)
		write(System.out);
	    if (DEBUG)
		System.out.println("  --> " + index);
	    changed();
	    return index;
	}
    }

    // TN added: The return value is the new index of the current move or -1 if
    // something failed.
    public int promoteVariation(int index) {
	if (DEBUG) {
	    System.out.println("promoteVariation " + index);
	    write(System.out);
	}
	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	VariationsModel varModel = new VariationsModel(m_moves, m_size);
	Map.Entry<Integer, Integer> upVar = varModel.getVariation(index);
	List<Integer> siblings = varModel.getSiblings(upVar.getKey());
	Map.Entry<Integer, Integer> downVar = varModel.getVariation(siblings.get(0) - 1);

	// for the return value
	int moveNumberInVariation = 0;
	int k = index;
	int level = 0;
	while (level >= 0) {
	    if (!Move.isSpecial(m_moves[k])) {
		++moveNumberInVariation;
	    } else if (m_moves[k] == LINE_START) {
		--level;
	    } else if (m_moves[k] == LINE_END) {
		++level;
	    }
	    --k;

	}

	short[] newMoves = new short[m_moves.length];
	int retVal = -1;
	try {
	    int copied = 0;
	    // copy everything from the beginning that remains unchanged
	    int copyIndex = siblings.get(0);
	    while (Move.isSpecial(m_moves[copyIndex])) {
		--copyIndex;
	    }
	    if (m_moves[copyIndex - 1] == PRE_COMMENT_END) {
		copyIndex -= 2;
		while (m_moves[copyIndex] != PRE_COMMENT_START) {
		    --copyIndex;
		}
	    }
	    System.arraycopy(m_moves, 0, newMoves, 0, copyIndex);
	    copied = copyIndex;

	    // copy the first move of the promoted line
	    int startIndex = upVar.getKey() + 1;
	    int endIndex = startIndex;
	    while (Move.isSpecial(m_moves[endIndex])) {
		++endIndex;
	    }
	    while (isNagValue(m_moves[endIndex + 1])) {
		++endIndex;
	    }
	    if (m_moves[endIndex + 1] == POST_COMMENT_END) {
		endIndex += 2;
		while (m_moves[endIndex] != POST_COMMENT_START) {
		    ++endIndex;
		}
	    }
	    int length1 = endIndex - startIndex + 1;
	    System.arraycopy(m_moves, startIndex, newMoves, copied, length1);
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
	    System.arraycopy(m_moves, copyIndex, newMoves, copied, siblings.get(0) - copyIndex);
	    copied += siblings.get(0) - copyIndex;

	    // copy the rest of the down-graded line
	    int lastSiblingIndex = varModel.getVariation(siblings.get(siblings.size() - 1)).getValue() + 1;
	    int lastDownGradedLine = downVar.getValue();
	    System.arraycopy(m_moves, lastSiblingIndex, newMoves, copied, lastDownGradedLine - lastSiblingIndex + 1);
	    copied += lastDownGradedLine - lastSiblingIndex + 1;

	    // copy the siblings before the promoted line
	    for (Integer siblingStart : siblings) {
		if (siblingStart < upVar.getKey()) { // predecessor
		    int seblingEnd = varModel.getVariation(siblingStart).getValue();
		    int length = seblingEnd - siblingStart + 1;
		    System.arraycopy(m_moves, siblingStart, newMoves, copied, length);
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
		    System.arraycopy(m_moves, siblingStart, newMoves, copied, length);
		    copied += length;
		} else {
		    break;
		}
	    }

	    // copy the rest of the promoted line
	    int firstRestFromPromoted = endIndex + 1;
	    int lastRestFromPromoted = upVar.getValue();
	    int length = lastRestFromPromoted - firstRestFromPromoted + 1;
	    System.arraycopy(m_moves, firstRestFromPromoted, newMoves, copied, length);
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
	    System.arraycopy(m_moves, copied, newMoves, copied, m_size - copied);

	} catch (Exception ex) {
	    // TN: as long as this code is not tested enough, crashes are avoided.
	    retVal = -1;
	}

	if (retVal >= 0) {
	    m_moves = newMoves;
	}
	return retVal;
    }

    // TN added:
    public void deleteRemainingMoves(int index) {
	if (DEBUG) {
	    System.out.println("deleteRemainingMoves " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	int level = 0;
	boolean deleteLineEnd = false;

	boolean inComment = false;
	while (index < m_size) {
	    short move = m_moves[index];
	    if (!inComment && move == LINE_START)
		level++;
	    else if (!inComment && move == LINE_END)
		level--;
	    else if (move == PRE_COMMENT_START || move == POST_COMMENT_START)
		inComment = true;
	    else if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		inComment = false;
	    if (level == -1) {
		if (deleteLineEnd)
		    m_moves[index] = NO_MOVE;
		break;
	    }
	    m_moves[index] = NO_MOVE;
	    index++;
	}
	changed();
	if (DEBUG)
	    write(System.out);

    }

    public void deleteCurrentLine(int index) {
	if (DEBUG) {
	    System.out.println("deleteCurrentLine " + index);
	    write(System.out);
	}

	if (EXTRA_CHECKS)
	    checkLegalCursor(index);

	int level = 0;
	boolean deleteLineEnd = false;

	// TN: the old version assume to be on a LINE_START already, because this
	// method was called only when a Game::goBackToLineBegin was called.
	// General, this assumption is wrong, and this became evident, when pre-move
	// comments where introduced.
//		// check if we stand at a line start
//		for (int i = 1; i < index; i++) {
//			short move = m_moves[index - i];
//			if (move == LINE_START) {
//				index -= i;
//				deleteLineEnd = true;
//				level = -1;
//				break;
//			} else if (move != NO_MOVE)
//				break;
//		}

	// TN version: go back to next LINE_START
	for (int i = index - 1; i >= 0; --i) {
	    short move = m_moves[i];
	    if (move == LINE_START) {
		index = i;
		deleteLineEnd = true;
		level = -1;
		break;
	    }
	}

	boolean inComment = false;
	while (index < m_size) {
	    short move = m_moves[index];
	    if (!inComment && move == LINE_START)
		level++;
	    else if (!inComment && move == LINE_END)
		level--;
	    else if (move == PRE_COMMENT_START || move == POST_COMMENT_START)
		inComment = true;
	    else if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		inComment = false;
	    if (level == -1) {
		if (deleteLineEnd)
		    m_moves[index] = NO_MOVE;
		break;
	    }
	    m_moves[index] = NO_MOVE;
	    index++;
	}
	changed();
	if (DEBUG)
	    write(System.out);
    }

    // ======================================================================
    // TN added:
    public void deleteAllLines() {
	if (DEBUG) {
	    System.out.println("deleteAllLines");
	    write(System.out);
	}

	int level = -1; // 0 is mainline
	for (int index = 0; index < m_size; ++index) {
	    short move = m_moves[index];
	    if (move == LINE_START) {
		++level;
	    }
	    if (level > 0) {
		m_moves[index] = NO_MOVE;
	    }
	    if (move == LINE_END) {
		--level;
	    }
	}
	changed();
	if (DEBUG)
	    write(System.out);

    }

    // ======================================================================

    public int pack(int index) {
	if (DEBUG) {
	    System.out.println("pack");
	    write(System.out);
	}

	int newSize = 0;
	for (int i = 0; i < m_size; i++) {
	    if (m_moves[i] != NO_MOVE)
		newSize++;
	}

	short[] newMoves = new short[newSize + 1];
	int j = 0;
	boolean inComment = false;
	for (int i = 0; i < m_size; i++) {
	    short move = m_moves[i];
	    if (move == PRE_COMMENT_START || move == POST_COMMENT_START)
		inComment = true;
	    else if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		inComment = false;
	    if (inComment || (move != NO_MOVE)) {
		newMoves[j++] = move;
	    }
	    if (i == index)
		index = j - 1;
	}

	m_moves = newMoves;
	m_moves[newSize] = LINE_END;
	m_size = newSize;

	if (DEBUG)
	    write(System.out);
	if (DEBUG)
	    System.out.println("  --> " + index);

	return index;
    }

    // ======================================================================

    public void load(DataInput in, int mode) throws IOException {
	m_size = in.readInt() + 2;
	m_moves = new short[m_size];
	byte[] data = new byte[2 * (m_size - 2)];
	in.readFully(data);
	for (int i = 1; i < m_size - 1; i++) {
	    // copied from RandomAccesFile.readShort
	    m_moves[i] = (short) ((data[2 * i - 2] << 8) | (data[2 * i - 1] & 0xFF));
	    // m_moves[i] = in.readShort();
	}
	m_moves[0] = LINE_START;
	m_moves[m_size - 1] = LINE_END;
	changed();
	if (DEBUG)
	    write(System.out);
    }

    public void save(DataOutput out, int mode) throws IOException {
	// do not save the guards at index 0 and m_size-1
	out.writeInt(m_size - 2);
	byte[] data = new byte[2 * (m_size - 2)];
	for (int i = 1; i < m_size - 1; i++) {
	    short m = m_moves[i];
	    // copied from RandomAccesFile.writeShort
	    data[2 * i - 2] = (byte) ((m >>> 8) & 0xFF);
	    data[2 * i - 1] = (byte) ((m >>> 0) & 0xFF);
	    // out.writeShort(m_moves[i]);
	}
	out.write(data);
    }

    // ======================================================================

    static String valueToString(short value) {
	if (value == LINE_START)
	    return "(";
	else if (value == LINE_END)
	    return ")";
	else if (value == NO_MOVE)
	    return "NO";
	else if (value == PRE_COMMENT_START || value == POST_COMMENT_START)
	    return "{";
	else if (value == PRE_COMMENT_END || value == POST_COMMENT_END)
	    return "}";
	else if (isNagValue(value))
	    return "$" + getNagForValue(value);
	else
	    return Move.getString(value);
    }

    public void write(PrintStream out) {
	boolean inComment = false;
	for (int i = 0; i < m_size; i++) {
	    short move = m_moves[i];
	    if (move == PRE_COMMENT_END || move == POST_COMMENT_END)
		inComment = false;
	    if (inComment) {
		out.print((char) move);
	    } else {
		out.print(valueToString(m_moves[i]));
		out.print(" ");
	    }
	    if ((i % 20) == 19)
		out.println();
	    if (move == PRE_COMMENT_START || move == POST_COMMENT_START)
		inComment = true;
	}
	out.println();
    }

    // ======================================================================

    // private static long[] s_rand = new long[65536];
    // static {
    // long randomNumber = 100;
    // for (int i=0; i<65536; i++) {
    // randomNumber = (randomNumber * 0x5DEECE66DL + 0xBL);
    // s_rand[i] = randomNumber;
    // }
    // }

    // private static int s_equals = 0, s_fullCompare = 0, s_true = 0, s_false =
    // 0;

    public long getHashCode() {
	if (m_hashCode == 0) {
	    int shift = 0;
	    for (int index = 0;; index = goForward(index)) {
		if (m_moves[index] == LINE_END)
		    break;
		short move = getMove(index);
		// m_hashCode ^= move;
		// m_hashCode += move;
		// m_hashCode += s_rand[(int)move - Short.MIN_VALUE];
		// m_hashCode ^= s_rand[(int)move - Short.MIN_VALUE];
		m_hashCode ^= (long) move << shift;
		if (shift == 12)
		    shift = 0;
		else
		    shift++;
	    }
	}
	return m_hashCode;
    }

    public int hashCode() {
	return (int) getHashCode();
    }

    public boolean equals(Object obj) {
	// s_equals++;
	if (obj == this)
	    return true; // =====>
	if (!(obj instanceof GameMoveModel))
	    return false; // =====>
	GameMoveModel gameMoveModel = (GameMoveModel) obj;

	if (gameMoveModel.getHashCode() != getHashCode())
	    return false; // =====>
	// s_fullCompare++;

	int index1 = 0, index2 = 0;
	for (;;) {
	    short move1 = m_moves[index1];
	    short move2 = gameMoveModel.m_moves[index2];
	    if (move1 == LINE_END && move2 == LINE_END)
		return true; // =====>
	    if (move1 != move2)
		return false; // =====>
	    // if (move1 == LINE_END && move2 == LINE_END) {s_true++;
	    // System.out.println(s_fullCompare + " / " + s_equals + " " +
	    // s_true + " " + s_false);return true;} // =====>
	    // if (move1 != move2) {s_false++; System.out.println(s_fullCompare
	    // + " / " + s_equals + " " + s_true + " " + s_false);return false;}
	    // // =====>
	    index1 = goForward(index1);
	    index2 = gameMoveModel.goForward(index2);
	}
    }

}
