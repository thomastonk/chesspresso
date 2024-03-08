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
package chesspresso.pgn;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.game.TraverseListener;
import chesspresso.move.Move;
import chesspresso.position.FEN;

/**
 * A PGN writer is able to write a game (collection) in PGN syntax.
 *
 * @author Bernhard Seybold
 * 
 */
public class PGNWriter {

	private final PrintWriter printWriter;
	private int charactersPerLine;
	private int curCol;

	/*
	 * =============================================================================
	 */

	public PGNWriter(Writer out) {
		this(new PrintWriter(out));
	}

	public PGNWriter(PrintWriter out) {
		printWriter = out;
		setCharactersPerLine(80);
	}

	/*
	 * =============================================================================
	 */

	public void setCharactersPerLine(int chars) {
		charactersPerLine = chars;
	}

	public void write(Game game) {
		writeHeader(game);
		printWriter.println();
		curCol = 0;
		writeMoves(game);
		if (curCol > 0) {
			printWriter.println();
		}
	}

	public void writeNewLine() {
		printWriter.println();
	}

	public static String writeToString(Game game) {
		StringWriter sw = new StringWriter();
		PGNWriter pgnWriter = new PGNWriter(sw);
		pgnWriter.write(game);
		return sw.toString();
	}

	public static String writeHeaderToString(Game game) {
		StringWriter sw = new StringWriter();
		PGNWriter pgnWriter = new PGNWriter(sw);
		pgnWriter.writeHeader(game);
		return sw.toString();
	}

	public static String writeMovesToString(Game game) {
		StringWriter sw = new StringWriter();
		PGNWriter pgnWriter = new PGNWriter(sw);
		pgnWriter.writeMoves(game);
		return sw.toString();
	}

	/*
	 * =============================================================================
	 */

	private void writeHeader(Game game) {
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_EVENT + " " + PGN.TOK_QUOTE + game.getEvent() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_SITE + " " + PGN.TOK_QUOTE + game.getSite() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_DATE + " " + PGN.TOK_QUOTE + game.getDate() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_ROUND + " " + PGN.TOK_QUOTE + game.getRound() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_WHITE + " " + PGN.TOK_QUOTE + game.getWhite() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_BLACK + " " + PGN.TOK_QUOTE + game.getBlack() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		printWriter.println(
				PGN.TOK_TAG_BEGIN + PGN.TAG_RESULT + " " + PGN.TOK_QUOTE + game.getResultStr() + PGN.TOK_QUOTE + PGN.TOK_TAG_END);

		String eloStr = game.getWhiteEloStr();
		if (eloStr != null && !eloStr.isBlank()) {
			printWriter.println(PGN.TOK_TAG_BEGIN + PGN.TAG_WHITE_ELO + " " + PGN.TOK_QUOTE + game.getWhiteElo() + PGN.TOK_QUOTE
					+ PGN.TOK_TAG_END);
		}
		eloStr = game.getBlackEloStr();
		if (eloStr != null && !eloStr.isBlank()) {
			printWriter.println(PGN.TOK_TAG_BEGIN + PGN.TAG_BLACK_ELO + " " + PGN.TOK_QUOTE + game.getBlackElo() + PGN.TOK_QUOTE
					+ PGN.TOK_TAG_END);
		}
		String eventDateStr = game.getEventDate();
		if (eventDateStr != null && !eventDateStr.isBlank()) {
			printWriter.println(PGN.TOK_TAG_BEGIN + PGN.TAG_EVENT_DATE + " " + PGN.TOK_QUOTE + game.getEventDate() + PGN.TOK_QUOTE
					+ PGN.TOK_TAG_END);
		}
		String ecoStr = game.getECO();
		if (ecoStr != null && !ecoStr.isBlank()) {
			printWriter.println(PGN.TOK_TAG_BEGIN + PGN.TAG_ECO + " " + PGN.TOK_QUOTE + ecoStr + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		}
		// TN: I think the following is nonsense: a FEN tag is needed if and only if the
		// start position of the game is not the usual one; but getPosition doesn't give
		// the start position!
		//        if (!game.getPosition().isStartPosition())
		//            out.println(TOK_TAG_BEGIN + TAG_FEN        + " " + TOK_QUOTE + FEN.getFEN(game.getPosition()) + TOK_QUOTE + TOK_TAG_END);
		// New code:
		Game copy = new Game(game);
		copy.gotoStart();
		if (!copy.getPosition().isStartPosition()) {
			printWriter.println(PGN.TOK_TAG_BEGIN + PGN.TAG_SET_UP + " " + PGN.TOK_QUOTE + "1" + PGN.TOK_QUOTE + PGN.TOK_TAG_END);
			printWriter.println(PGN.TOK_TAG_BEGIN + PGN.TAG_FEN + " " + PGN.TOK_QUOTE + FEN.getFEN(copy.getPosition())
					+ PGN.TOK_QUOTE + PGN.TOK_TAG_END);
		}
		// The copy is used to prevent side effects; a game.gotoStart() would do the
		// same job, but if the game is shown in a game browser, it would be set to the
		// start position, too.

		// TN: And the (further) additional tags were missing!
		String[] otherTags = game.getOtherTags();
		if (otherTags != null) {
			for (String otherTag : otherTags) {
				if (otherTag.equals(PGN.TAG_FEN) || otherTag.equals(PGN.TAG_SET_UP)) {
					continue;
				}
				printWriter.println(PGN.TOK_TAG_BEGIN + otherTag + " " + PGN.TOK_QUOTE + game.getTag(otherTag) + PGN.TOK_QUOTE
						+ PGN.TOK_TAG_END);
			}
		}
	}

	private void writeMoves(Game game) {
		if (game.getNumOfPlies() > 0) {
			game.traverse(new TraverseListener() {
				private boolean needsMoveNumber = true;

				@Override
				public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber,
						int level, String fenBeforeMove) {
					if (preMoveComment != null) {
						print(PGN.TOK_COMMENT_BEGIN + preMoveComment + PGN.TOK_COMMENT_END, true);
					}
					if (needsMoveNumber) {
						if (move.isWhiteMove()) {
							print(Chess.plyToMoveNumber(plyNumber) + ".", true);
						} else {
							print(Chess.plyToMoveNumber(plyNumber) + "...", true);
						}
					}
					print(move.toString(), true);

					if (nags != null) {
						for (short nag : nags) {
							print(PGN.TOK_NAG_BEGIN + String.valueOf(nag), true);
						}
					}
					if (postMoveComment != null) {
						print(PGN.TOK_COMMENT_BEGIN + postMoveComment + PGN.TOK_COMMENT_END, true);
					}
					needsMoveNumber = !move.isWhiteMove() || (postMoveComment != null);
				}

				@Override
				public void notifyLineStart(int level) {
					print(String.valueOf(PGN.TOK_LINE_BEGIN), false);
					needsMoveNumber = true;
				}

				@Override
				public void notifyLineEnd(int level) {
					print(String.valueOf(PGN.TOK_LINE_END), true);
					needsMoveNumber = true;
				}
			}, true);
		} else {
			String s = game.getEmptyGameComment();
			if (s != null && !s.isEmpty()) {
				print(PGN.TOK_COMMENT_BEGIN + s + PGN.TOK_COMMENT_END, true);
			}
		}

		print(game.getResultStr(), false);
	}

	private void print(String s, boolean addSpace) {
		if (s == null) {
			return;
		}
		if (curCol > 0 && curCol + s.length() > charactersPerLine) {
			printWriter.println();
			curCol = 0;
		}
		printWriter.print(s);
		curCol += s.length();
		if (curCol > 0 && addSpace) {
			printWriter.print(" ");
			curCol += 1;
		}
	}
}
