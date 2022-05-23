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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedList;

import chesspresso.Chess;
import chesspresso.pgn.PGN;

/**
 *
 * @author Bernhard Seybold
 * 
 */
class GameHeaderModel implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private static final int INDEX_EVENT = 0, INDEX_SITE = 1, INDEX_DATE = 2, INDEX_ROUND = 3, INDEX_WHITE = 4, INDEX_BLACK = 5,
			INDEX_RESULT = 6, INDEX_WHITE_ELO = 7, INDEX_BLACK_ELO = 8, INDEX_EVENT_DATE = 9, INDEX_ECO = 10,
			NUM_OF_STANDARD_TAGS = 11;

	private static final String[] TAG_NAMES = { PGN.TAG_EVENT, PGN.TAG_SITE, PGN.TAG_DATE, PGN.TAG_ROUND, PGN.TAG_WHITE,
			PGN.TAG_BLACK, PGN.TAG_RESULT, PGN.TAG_WHITE_ELO, PGN.TAG_BLACK_ELO, PGN.TAG_EVENT_DATE, PGN.TAG_ECO };

	public static final int MODE_SEVEN_TAG_ROASTER = 0, // need to be consecutive!
			MODE_STANDARD_TAGS = 1, MODE_ALL_TAGS = 2;

	/*
	 * =============================================================================
	 */

	private String[] m_standardTags;
	private LinkedList<String> m_otherTags;
	private LinkedList<String> m_otherTagValues;

	/*
	 * =============================================================================
	 */

	GameHeaderModel() {
		m_standardTags = new String[NUM_OF_STANDARD_TAGS];
		m_otherTags = null;
	}

	GameHeaderModel(DataInput in, int mode) throws IOException {
		m_standardTags = new String[NUM_OF_STANDARD_TAGS];
		m_otherTags = null;
		load(in, mode);
	}

	@SuppressWarnings("unchecked")
	GameHeaderModel getDeepCopy() {
		GameHeaderModel copy = new GameHeaderModel();
		copy.m_standardTags = new String[this.m_standardTags.length];
		System.arraycopy(this.m_standardTags, 0, copy.m_standardTags, 0, copy.m_standardTags.length);
		if (this.m_otherTags != null) {
			copy.m_otherTags = new LinkedList<>();
			copy.m_otherTags = (LinkedList<String>) this.m_otherTags.clone();
		}
		if (this.m_otherTagValues != null) {
			copy.m_otherTagValues = new LinkedList<>();
			copy.m_otherTagValues = (LinkedList<String>) this.m_otherTagValues.clone();
		}
		return copy;
	}

	/*
	 * =============================================================================
	 */

	private int getStandardTagIndex(String tagName) {
		for (int i = 0; i < NUM_OF_STANDARD_TAGS; i++) {
			if (TAG_NAMES[i].equals(tagName))
				return i;
		}
		return -1;
	}

	String getTag(String tagName) {
		int index = getStandardTagIndex(tagName);
		if (index != -1) {
			return m_standardTags[index];
		} else if (m_otherTags != null) {
			index = m_otherTags.indexOf(tagName);
			return (index == -1 ? null : m_otherTagValues.get(index));
		} else {
			return null;
		}
	}

	void setTag(String tagName, String tagValue) {
		// TN: the dependency between the tags SetUp and FEN is not solved here.
		// So far, it is only treated in PGNWriter::writeHeader.
		int index = getStandardTagIndex(tagName);
		if (index != -1) {
			m_standardTags[index] = tagValue;
		} else if (!"PlyCount".equals(tagName)) {
			if (m_otherTags == null) {
				m_otherTags = new LinkedList<>();
				m_otherTagValues = new LinkedList<>();
			}
			index = m_otherTags.indexOf(tagName);
			if (index == -1) {
				m_otherTags.addLast(tagName);
				m_otherTagValues.addLast(tagValue); // append
			} else {
				m_otherTagValues.set(index, tagValue); // replace
			}
		}
	}

	String[] getTags() {
		int numOfTags = (m_otherTags == null ? 0 : m_otherTags.size());
		for (int i = 0; i < NUM_OF_STANDARD_TAGS; i++)
			if (m_standardTags[i] != null)
				numOfTags++;

		String[] tags = new String[numOfTags];
		int index = 0;
		for (int i = 0; i < NUM_OF_STANDARD_TAGS; i++) {
			if (m_standardTags[i] != null)
				tags[index++] = TAG_NAMES[i];
		}
		if (m_otherTags != null) {
			for (String m_otherTag : m_otherTags) {
				tags[index++] = m_otherTag;
			}
		}
		return tags;
	}

	String[] getOtherTags() {
		int numOfTags = (m_otherTags == null ? 0 : m_otherTags.size());

		if (numOfTags == 0) {
			return null;
		} else {
			String[] tags = new String[numOfTags];
			int index = 0;
			if (m_otherTags != null) {
				for (String m_otherTag : m_otherTags) {
					tags[index++] = m_otherTag;
				}
			}
			return tags;
		}
	}

	void clearTags() {
		m_standardTags = new String[NUM_OF_STANDARD_TAGS];
		m_otherTags = null;
	}

	void removeTag(String tagName) {
		int standardIndex = getStandardTagIndex(tagName);
		if (standardIndex != -1) {
			m_standardTags[standardIndex] = "";
		} else if (m_otherTags != null && tagName != null) {
			for (int index = 0; index < m_otherTags.size(); ++index) {
				if (tagName.equals(m_otherTags.get(index))) {
					m_otherTags.remove(index);
					m_otherTagValues.remove(index);
					--index; // since removeIf is no choice here
				}
			}
		}
	}

	/*
	 * =============================================================================
	 */
	// convenience methods for tags

	private String getStandardTag(int index) {
		String tag = m_standardTags[index];
		return tag == null ? "" : tag;
	}

	String getEvent() {
		return getStandardTag(INDEX_EVENT);
	}

	String getSite() {
		return getStandardTag(INDEX_SITE);
	}

	String getDate() {
		return getStandardTag(INDEX_DATE);
	}

	String getRound() {
		return getStandardTag(INDEX_ROUND);
	}

	String getWhite() {
		return getStandardTag(INDEX_WHITE);
	}

	String getBlack() {
		return getStandardTag(INDEX_BLACK);
	}

	String getResultStr() {
		return getStandardTag(INDEX_RESULT);
	}

	String getWhiteEloStr() {
		return getStandardTag(INDEX_WHITE_ELO);
	}

	String getBlackEloStr() {
		return getStandardTag(INDEX_BLACK_ELO);
	}

	String getEventDate() {
		return getStandardTag(INDEX_EVENT_DATE);
	}

	String getECO() {
		return getStandardTag(INDEX_ECO);
	}

	int getResult() {
		// SEYBOLD: combine with PGNReader.isResultString
		// TN: I assume PGNReader.getLastTokenAsResult is meant. Chess.java could
		// get the code for string detection, e.g. the if/else below, and both methods
		// could apply it. Not trivial.
		String result = getResultStr();
		if ("1-0".equals(result))
			return Chess.RES_WHITE_WINS;
		else if ("0-1".equals(result))
			return Chess.RES_BLACK_WINS;
		else if ("1/2-1/2".equals(result))
			return Chess.RES_DRAW;
		else if ("*".equals(result))
			return Chess.RES_NOT_FINISHED;
		else
			return Chess.NO_RES;
	}

	int getWhiteElo() {
		try {
			String whiteElo = getWhiteEloStr();
			if (whiteElo == null)
				return 0; // =====>
			return Integer.parseInt(whiteElo);
		} catch (NumberFormatException ex) {
			return 0; // =====>
		}
	}

	int getBlackElo() {
		try {
			String blackElo = getBlackEloStr();
			if (blackElo == null)
				return 0; // =====>
			return Integer.parseInt(blackElo);
		} catch (NumberFormatException ex) {
			return 0; // =====>
		}
	}

	/*
	 * =============================================================================
	 */

	private String readUTFNonNull(DataInput in) throws IOException {
		String s = in.readUTF();
		return s.equals("") ? null : s;
	}

	void load(DataInput in, int mode) throws IOException {
		setTag(PGN.TAG_EVENT, readUTFNonNull(in));
		setTag(PGN.TAG_SITE, readUTFNonNull(in));
		setTag(PGN.TAG_DATE, readUTFNonNull(in));
		setTag(PGN.TAG_ROUND, readUTFNonNull(in));
		setTag(PGN.TAG_WHITE, readUTFNonNull(in));
		setTag(PGN.TAG_BLACK, readUTFNonNull(in));
		setTag(PGN.TAG_RESULT, readUTFNonNull(in));

		if (mode <= MODE_SEVEN_TAG_ROASTER)
			return; // =====>

		setTag(PGN.TAG_WHITE_ELO, readUTFNonNull(in));
		setTag(PGN.TAG_BLACK_ELO, readUTFNonNull(in));
		setTag(PGN.TAG_EVENT_DATE, readUTFNonNull(in));
		setTag(PGN.TAG_ECO, readUTFNonNull(in));

		if (mode <= MODE_STANDARD_TAGS)
			return; // =====>

		// NOT YET SUPPORTED
	}

	private void writeUTFNonNull(DataOutput out, String s) throws IOException {
		out.writeUTF(s == null ? "" : s);
	}

	void save(DataOutput out, int mode) throws IOException {
		writeUTFNonNull(out, getEvent());
		writeUTFNonNull(out, getSite());
		writeUTFNonNull(out, getDate());
		writeUTFNonNull(out, getRound());
		writeUTFNonNull(out, getWhite());
		writeUTFNonNull(out, getBlack());
		writeUTFNonNull(out, getResultStr());

		if (mode <= MODE_SEVEN_TAG_ROASTER)
			return; // =====>

		writeUTFNonNull(out, getWhiteEloStr());
		writeUTFNonNull(out, getBlackEloStr());
		writeUTFNonNull(out, getEventDate());
		writeUTFNonNull(out, getECO());

		if (mode <= MODE_STANDARD_TAGS)
			return; // =====>

		// NOT YET SUPPORTED
	}

	/*
	 * ===================================================================
	 */

	private static boolean isStringSimilar(String s1, String s2) {
		if (s1 == null) {
			return s2 == null;
		} else if (s2 == null) {
			return false;
		} else {
			int hits = 0, total = 0;
			s2 = s2.toLowerCase();
			for (int i = 0; i < s1.length(); i++) {
				char ch = Character.toLowerCase(s1.charAt(i));
				if (!Character.isWhitespace(ch)) {
					total++;
					int index = s2.indexOf(ch);
					if (index != -1) {
						hits++;
						s2 = s2.substring(0, index) + s2.substring(index + 1);
					}
				}
			}
			return (2 * hits >= total);
		}
	}

	boolean isSimilar(GameHeaderModel headerModel) {
		return isStringSimilar(getWhite(), headerModel.getWhite()) && isStringSimilar(getBlack(), headerModel.getBlack());
	}

	/*
	 * =============================================================================
	 */

	@Override
	public String toString() {
		return getWhite() + " - " + getBlack() + " " + getResultStr() + " (" + getDate() + ")";
	}
}
