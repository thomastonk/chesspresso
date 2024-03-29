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
package chesspresso.game.view;

import java.io.OutputStream;
import java.io.PrintStream;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.game.TraverseListener;
import chesspresso.move.Move;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.NAG;
import chesspresso.position.Position;

/**
 * Producer for HTML pages displaying a game.
 *
 * @author Bernhard Seybold
 * 
 */
public class HTMLGameBrowser implements TraverseListener {

	private StringBuffer m_moves;
	private StringBuffer m_posData;
	private StringBuffer m_lastData;
	private Game m_game;
	private int m_moveNumber;
	private boolean m_showMoveNumber;
	private int[] m_lasts;

	// ======================================================================
	// TraverseListener Methods

	@Override
	public void notifyLineStart(int level) {
		m_moves.append(" (");
		m_showMoveNumber = true;
		m_lasts[level + 1] = m_lasts[level];
	}

	@Override
	public void notifyLineEnd(int level) {
		m_moves.append(") ");
		m_showMoveNumber = true;
	}

	private void addPosData(ImmutablePosition pos) {
		m_posData.append("  sq[").append(m_moveNumber).append("] = new Array(");
		for (int row = Chess.NUM_OF_ROWS - 1; row >= 0; row--) {
			for (int col = 0; col < Chess.NUM_OF_COLS; col++) {
				int sqi = Chess.coorToSqi(col, row);
				if (sqi != Chess.A8) {
					m_posData.append(",");
				}
				m_posData.append(pos.getStone(sqi) - Chess.MIN_STONE);
			}
		}
		m_posData.append(");\n");
	}

	@Override
	public void notifyMove(Move move, short[] nags, String preMoveComment, String postMoveComment, int plyNumber, int level) {
		ImmutablePosition pos = m_game.getPosition();

		boolean isMainLine = (level == 0);
		String type = isMainLine ? "main" : "line";

		if (preMoveComment != null) {
			m_moves.append("<span class=\"comment\">").append(preMoveComment).append("</span> ");
		}

		m_moves.append("<a name=\"").append(m_moveNumber).append("\" class=\"").append(type).append("\" href=\"javascript:go(")
				.append(m_moveNumber).append(")\">");
		if (m_showMoveNumber) {
			m_moves.append(plyNumber / 2 + 1).append(".");
		}
		m_showMoveNumber = Chess.isWhitePly(plyNumber + 1);

		m_moves.append(move.toString());
		if (nags != null) {
			for (short nag : nags) {
				m_moves.append(NAG.getShortString(nag));
			}
			m_showMoveNumber = true;
		}
		m_moves.append("</a> ");
		if (postMoveComment != null) {
			m_moves.append("<span class=\"comment\">").append(postMoveComment).append("</span> ");
		}

		addPosData(pos);
		m_lastData.append(",").append(m_lasts[level]);
		m_lasts[level] = m_moveNumber;

		m_moveNumber++;
	}

	// ======================================================================

	private final String[] m_wimgs;
	private final String[] m_bimgs;
	private String m_imagePrefix;
	private String m_styleFilename;

	// ======================================================================

	/**
	 * Create a new HTMLGameBrowser with default settings.
	 */
	public HTMLGameBrowser() {
		m_wimgs = new String[] { "wkw.gif", "wpw.gif", "wqw.gif", "wrw.gif", "wbw.gif", "wnw.gif", "now.gif", "bnw.gif",
				"bbw.gif", "brw.gif", "bqw.gif", "bpw.gif", "bkw.gif" };
		m_bimgs = new String[] { "wkb.gif", "wpb.gif", "wqb.gif", "wrb.gif", "wbb.gif", "wnb.gif", "nob.gif", "bnb.gif",
				"bbb.gif", "brb.gif", "bqb.gif", "bpb.gif", "bkb.gif" };
		m_imagePrefix = "";
		m_styleFilename = null;
	}

	// ======================================================================

	/**
	 * Set the name of the style file. If name is set to null, inline style
	 * definition will be used. Default is inline style.<br>
	 * When using an external style file, the following styles are expected:
	 * <ul>
	 * <li>a.main: the anchor used for moves in the main line
	 * <li>a.line: the anchor used for moves in side-lines
	 * <li>span.comment: used for move comments
	 * <li>table.content: the content table containing the board left and the moves
	 * on the right
	 * </ul>
	 *
	 * @param styleFilename the name of the style file
	 */
	@SuppressWarnings("unused")
	private void setStyleFilename(String styleFilename) {
		m_styleFilename = styleFilename;
	}

	/**
	 * Set the prefix for images. The default is empty.
	 *
	 * @param imagePrefix the prefix for images, must contain trailing slashes
	 */
	@SuppressWarnings("unused")
	private void setImagePrefix(String imagePrefix) {
		m_imagePrefix = imagePrefix;
	}

	/**
	 * Sets the name of an square image. The default names are set according to the
	 * following scheme: First letter is the color of the stone (b, w), second
	 * letter the piece (k, q, r, b, n, p) third letter the square color (b, w),
	 * extension is gif. now.gif and nob.gif are used for empty squares.<br>
	 * For instance: wkw.gif determines a white king on a white square, bbb.gif is a
	 * black bishop on a black square.
	 *
	 * @param stone       the stone displayed
	 * @param whiteSquare whether or not the square is white
	 * @param name        the name of the corresponding image
	 */
	@SuppressWarnings("unused")
	private void setStoneImageName(int stone, boolean whiteSquare, String name) {
		if (whiteSquare) {
			m_wimgs[stone - Chess.MIN_STONE] = name;
		} else {
			m_bimgs[stone - Chess.MIN_STONE] = name;
		}
	}

	/**
	 * Returns the name of the image.
	 *
	 * @param stone       the stone displayed
	 * @param isWhite whether or not the square is white
	 */
	private String getImageForStone(int stone, boolean isWhite) {
		return m_imagePrefix + (isWhite ? m_wimgs[stone - Chess.MIN_STONE] : m_bimgs[stone - Chess.MIN_STONE]);
	}

	// ======================================================================

	/**
	 * Produces HTML to display a game.
	 *
	 * @param outStream where the HTML will be sent to
	 * @param game      the game to display.
	 */
	public void produceHTML(OutputStream outStream, Game game) {
		produceHTML(outStream, game, false);
	}

	/**
	 * Produces HTML to display a game.
	 *
	 * @param outStream   where the HTML will be sent to
	 * @param game        the game to display.
	 * @param contentOnly if true skip header and footer information, use this if
	 *                    you want to produce your own header and footer
	 */
	public synchronized void produceHTML(OutputStream outStream, Game game, boolean contentOnly) {
		PrintStream out = new PrintStream(outStream);

		m_moves = new StringBuffer();
		m_posData = new StringBuffer();
		m_lastData = new StringBuffer();
		m_game = game;
		m_moveNumber = 0;
		m_showMoveNumber = true;
		m_lasts = new int[100];
		m_lasts[0] = 0;

		m_posData.append("  sq = new Array(").append(game.getNumOfPlies()).append("); ");
		m_lastData.append("  last=new Array(0");

		m_game.gotoStart();
		addPosData(m_game.getPosition());
		m_moveNumber++;

		m_moves.append("<h4>").append(m_game).append("</h4>");

		game.traverse(this, true);

		m_moves.append(" ").append(game.getResultStr());
		m_lastData.append(");");

		if (!contentOnly) {
			out.println(
					"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
			out.println("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"de\">");
			out.println("<html>");
			out.println("<head>");
			out.println("<meta name=\"generator\" content=\"Chesspresso\" />");
			out.println("<title>" + m_game + "</title>");
			if (m_styleFilename == null) {
				out.println("<style type=\"text/css\">");
				out.println("   .main {text-decoration:none}");
				out.println("   .line {text-decoration:none}");
				out.println("  a.main {font-weight:bold; color:black}");
				out.println("  a.line {color:black}");
				out.println("  table.content {cell-spacing:20}");
				out.println("  span.comment {font-style:italic}");
				out.println("</style>");
			} else {
				out.println("<link rel=\"stylesheet\" href=\"" + m_styleFilename + "\" type=\"text/css\" />");
			}

			out.println("<script language=\"JavaScript\">");
			out.println("  moveNumber = 0;");
			out.print("  imgs = new Array(");
			for (int stone = Chess.MIN_STONE; stone <= Chess.MAX_STONE; stone++) {
				out.print("'" + getImageForStone(stone, true) + "',");
			}
			for (int stone = Chess.MIN_STONE; stone <= Chess.MAX_STONE; stone++) {
				out.print("'" + getImageForStone(stone, false) + "'");
				if (stone < Chess.MAX_STONE) {
					out.print(",");
				}
			}
			out.println(");");

			//        out.println("function go(num) {window.document.anchors[moveNumber-1].style.background=\"white\"; if (num<0) moveNumber=0; else if (num>" + (m_moveNumber - 1) + ") moveNumber=" + (m_moveNumber - 1) + "; else moveNumber=num; for(i=0;i<64;i++){if ((Math.floor(i/8)%2)==(i%2)) window.document.images[i].src=wimgs[sq[num][i]]; else window.document.images[i].src=bimgs[sq[num][i]];}; window.document.anchors[moveNumber-1].style.background=\"black\";}");
			out.println("  function go(num) {");
			// TO_DO style for selected move
			out.println(
					"    if (moveNumber>0) {window.document.anchors[moveNumber-1].style.background=\"white\"; window.document.anchors[moveNumber-1].style.color=\"black\";}");
			out.println("    if (num<0) moveNumber=0;");
			out.println("    else if (num>" + (m_moveNumber - 1) + ") moveNumber=" + (m_moveNumber - 1) + ";");
			out.println("    else moveNumber=num;");
			out.println("    for(i=0;i<64;i++){");
			out.println("      if ((Math.floor(i/8)%2)==(i%2)) offset=0; else offset=13;");
			out.println("      window.document.images[i].src=imgs[sq[num][i]+offset];");
			out.println("    }");
			out.println(
					"    if (moveNumber>0) {window.document.anchors[moveNumber-1].style.background=\"black\"; window.document.anchors[moveNumber-1].style.color=\"white\";}");
			out.println("  }");
			out.println("  function gotoStart() {go(0);}");
			out.println("  function goBackward() {go(last[moveNumber]);}");
			out.println("  function goForward() {for (i=" + m_moveNumber
					+ "; i>moveNumber; i--) if (last[i]==moveNumber) {go(i); break;}}");
			out.println("  function gotoEnd() {go(" + (m_moveNumber - 1) + ");}");
			out.println(m_posData.toString());
			out.println(m_lastData.toString());
			out.println("</script>");
			out.println();

			out.println("</head>");
			out.println();

			out.println("<body>");
		}

		out.println("<table class=\"content\"><tr><td valign=\"top\">");

		out.println("<table cellspacing=\"0\" cellpadding=\"0\">");
		Position startPos = Position.createInitialPosition();
		for (int row = Chess.NUM_OF_ROWS - 1; row >= 0; row--) {
			out.print("  <tr>");
			for (int col = 0; col < Chess.NUM_OF_COLS; col++) {
				int sqi = Chess.coorToSqi(col, row);
				out.print("<td><img src=\"" + getImageForStone(startPos.getStone(sqi), Chess.isWhiteSquare(sqi)) + "\"></td>");
			}
			out.println("</tr>");
		}
		out.println("</table>");
		out.println("<center><form name=\"tapecontrol\">");
		out.println("<input type=button value=\" Start \" onClick=\"gotoStart();\" onDblClick=\"gotoStart();\">");
		out.println("<input type=button value=\" &lt; \" onClick=\"goBackward();\" onDblClick=\"goBackward();\">");
		out.println("<input type=button value=\" &gt; \" onClick=\"goForward();\" onDblClick=\"goForward();\">");
		out.println("<input type=button value=\" End \" onClick=\"gotoEnd();\" onDblClick=\"gotoEnd();\">");
		out.println("</form></center>");
		out.println();

		out.println("</td><td valign=\"top\">");
		out.println(m_moves.toString());
		out.println("</td</tr></table>");

		if (!contentOnly) {
			out.println("</body></html>");
		}
	}

	//    public static void main(String[] args)
	//    {
	//        try {
	//            chesspresso.pgn.PGNReader pgn = new chesspresso.pgn.PGNReader(args[0]);
	//            Game game = pgn.parseGame();
	//            System.out.println(game);
	//
	//            HTMLGameBrowser html = new HTMLGameBrowser();
	//            html.produceHTML(System.out, game);
	//        } catch (Exception ex) {
	//            ex.printStackTrace();
	//        }
	//    }
	//
}
