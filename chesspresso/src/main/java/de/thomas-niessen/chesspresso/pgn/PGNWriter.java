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
package chesspresso.pgn;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.game.GameListener;
import chesspresso.game.GameModel;
import chesspresso.game.GameModelIterator;
import chesspresso.move.Move;
import chesspresso.position.FEN;

/**
 * A PGN writer is able to write a game (collection) in PGN syntax.
 *
 * @author Bernhard Seybold
 * @version $Revision: 1.2 $
 */
public class PGNWriter extends PGN {

    private PrintWriter m_out;
    private int m_charactersPerLine;
    private int m_curCol;

    /*
     * =============================================================================
     * ===
     */

    public PGNWriter(Writer out) {
	this(new PrintWriter(out));
    }

    public PGNWriter(PrintWriter out) {
	m_out = out;
	setCharactersPerLine(80);
    }

    /*
     * =============================================================================
     * ===
     */

    public void setCharactersPerLine(int chars) {
	m_charactersPerLine = chars;
    }

    public void write(GameModelIterator iterator) {
	while (iterator.hasNext()) {
	    write(iterator.nextGameModel());
	    m_out.println();
	}
    }

    public void write(GameModel gameModel) {
	Game game = new Game(gameModel);
	writeHeader(game);
	m_out.println();
	m_curCol = 0;
	writeMoves(game);
	if (m_curCol > 0)
	    m_out.println();
    }

    public void write(Game game) {
	writeHeader(game);
	m_out.println();
	m_curCol = 0;
	writeMoves(game);
	if (m_curCol > 0)
	    m_out.println();
    }

    public void writeNewLine() {
	m_out.println();
    }

    public static String writeToString(Game game) {
	StringWriter sw = new StringWriter();
	PGNWriter pgnWriter = new PGNWriter(sw);
	pgnWriter.write(game);
	return sw.toString();
    }

    /*
     * =============================================================================
     * ===
     */

    private void writeHeader(Game game) {
	m_out.println(TOK_TAG_BEGIN + TAG_EVENT + " " + TOK_QUOTE + game.getEvent() + TOK_QUOTE + TOK_TAG_END);
	m_out.println(TOK_TAG_BEGIN + TAG_SITE + " " + TOK_QUOTE + game.getSite() + TOK_QUOTE + TOK_TAG_END);
	m_out.println(TOK_TAG_BEGIN + TAG_DATE + " " + TOK_QUOTE + game.getDate() + TOK_QUOTE + TOK_TAG_END);
	m_out.println(TOK_TAG_BEGIN + TAG_ROUND + " " + TOK_QUOTE + game.getRound() + TOK_QUOTE + TOK_TAG_END);
	m_out.println(TOK_TAG_BEGIN + TAG_WHITE + " " + TOK_QUOTE + game.getWhite() + TOK_QUOTE + TOK_TAG_END);
	m_out.println(TOK_TAG_BEGIN + TAG_BLACK + " " + TOK_QUOTE + game.getBlack() + TOK_QUOTE + TOK_TAG_END);
	m_out.println(TOK_TAG_BEGIN + TAG_RESULT + " " + TOK_QUOTE + game.getResultStr() + TOK_QUOTE + TOK_TAG_END);

	if (game.getWhiteEloStr() != null)
	    m_out.println(
		    TOK_TAG_BEGIN + TAG_WHITE_ELO + " " + TOK_QUOTE + game.getWhiteElo() + TOK_QUOTE + TOK_TAG_END);
	if (game.getBlackEloStr() != null)
	    m_out.println(
		    TOK_TAG_BEGIN + TAG_BLACK_ELO + " " + TOK_QUOTE + game.getBlackElo() + TOK_QUOTE + TOK_TAG_END);
	if (game.getEventDate() != null)
	    m_out.println(
		    TOK_TAG_BEGIN + TAG_EVENT_DATE + " " + TOK_QUOTE + game.getEventDate() + TOK_QUOTE + TOK_TAG_END);
	if (game.getECO() != null)
	    m_out.println(TOK_TAG_BEGIN + TAG_ECO + " " + TOK_QUOTE + game.getECO() + TOK_QUOTE + TOK_TAG_END);
	// TN: I think the following is nonsense: a FEN tag is needed if and only if the
	// start position of the game is not the usual one; but getPosition doesn't give
	// the start position!
//        if (!game.getPosition().isStartPosition())
//            m_out.println(TOK_TAG_BEGIN + TAG_FEN        + " " + TOK_QUOTE + FEN.getFEN(game.getPosition()) + TOK_QUOTE + TOK_TAG_END);
	// New code:
	Game copy = new Game(game.getModel());
	copy.gotoStart();
	if (!copy.getPosition().isStartPosition()) {
	    m_out.println(TOK_TAG_BEGIN + TAG_SET_UP + " " + TOK_QUOTE + "1" + TOK_QUOTE + TOK_TAG_END);
	    m_out.println(TOK_TAG_BEGIN + TAG_FEN + " " + TOK_QUOTE + FEN.getFEN(copy.getPosition()) + TOK_QUOTE
		    + TOK_TAG_END);
	}
	// The copy is used to prevent side effects; a game.gotoStart() would do the
	// same job, but if the game is shown in a game browser, it would be set to the
	// start position, too.

	// TN: And the (further) additional tags were missing!
	String[] otherTags = game.getModel().getHeaderModel().getOtherTags();
	if (otherTags != null) {
	    for (String otherTag : otherTags) {
		if (otherTag.equals(TAG_FEN) || otherTag.equals(TAG_SET_UP)) {
		    continue;
		}
		m_out.println(
			TOK_TAG_BEGIN + otherTag + " " + TOK_QUOTE + game.getTag(otherTag) + TOK_QUOTE + TOK_TAG_END);
	    }
	}
    }

    private void writeMoves(Game game) {
	if (game.getNumOfPlies() > 0) {
	    game.traverse(new GameListener() {
		private boolean needsMoveNumber = true;

		public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment,
			int plyNumber, int level) {
		    if (preMoveComment != null)
			print(TOK_COMMENT_BEGIN + preMoveComment + TOK_COMMENT_END, true);
		    if (needsMoveNumber) {
			if (move.isWhiteMove()) {
			    print(Chess.plyToMoveNumber(plyNumber) + ".", true);
			} else {
			    print(Chess.plyToMoveNumber(plyNumber) + "...", true);
			}
		    }
		    print(move.toString(), true);

		    if (nags != null) {
			for (int i = 0; i < nags.length; i++) {
			    print(String.valueOf(TOK_NAG_BEGIN) + String.valueOf(nags[i]), true);
			}
		    }
		    if (postMoveComment != null)
			print(TOK_COMMENT_BEGIN + postMoveComment + TOK_COMMENT_END, true);
		    needsMoveNumber = !move.isWhiteMove() || (postMoveComment != null);
		}

		public void notifyLineStart(int level) {
		    print(String.valueOf(TOK_LINE_BEGIN), false);
		    needsMoveNumber = true;
		}

		public void notifyLineEnd(int level) {
		    print(String.valueOf(TOK_LINE_END), true);
		    needsMoveNumber = true;
		}
	    }, true);
	} else {
	    String s = game.getEmptyGameComment();
	    if (s != null && !s.isEmpty()) {
		print(TOK_COMMENT_BEGIN + s + TOK_COMMENT_END, true);
	    }
	}

	print(game.getResultStr(), false);
    }

    private void print(String s, boolean addSpace) {
	if (s == null)
	    return;
	if (m_curCol + s.length() > m_charactersPerLine) {
	    m_out.println();
	    m_curCol = 0;
	}
	m_out.print(s);
	m_curCol += s.length();
	if (m_curCol > 0 && addSpace) {
	    m_out.print(" ");
	    m_curCol += 1;
	}
    }
}
