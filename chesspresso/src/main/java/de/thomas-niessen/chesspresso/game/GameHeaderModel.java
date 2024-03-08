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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chesspresso.Chess;
import chesspresso.pgn.PGN;

/**
 * @author Bernhard Seybold
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

	// =============================================================================

	private String[] standardTags;
	private List<String> otherTags;
	private List<String> otherTagValues;

	// =============================================================================

	GameHeaderModel() {
		standardTags = new String[NUM_OF_STANDARD_TAGS];
		otherTags = null;
	}

	GameHeaderModel getDeepCopy() {
		GameHeaderModel copy = new GameHeaderModel();
		copy.setByCopying(this);
		return copy;
	}

	void setByCopying(GameHeaderModel otherModel) {
		if (this == otherModel) {
			return;
		}
		// standard tags
		System.arraycopy(otherModel.standardTags, 0, standardTags, 0, NUM_OF_STANDARD_TAGS);
		// other tags
		if (otherModel.otherTags == null) {
			otherTags = null;
			otherTagValues = null;
		} else {
			if (otherTags == null) {
				otherTags = new ArrayList<>();
				otherTagValues = new ArrayList<>();
			} else {
				otherTags.clear();
				otherTagValues.clear();
			}
			for (int i = 0; i < otherModel.otherTags.size(); ++i) {
				otherTags.add(otherModel.otherTags.get(i));
				otherTagValues.add(otherModel.otherTagValues.get(i));
			}
		}
	}

	// =============================================================================

	private int getStandardTagIndex(String tagName) {
		for (int i = 0; i < NUM_OF_STANDARD_TAGS; i++) {
			if (TAG_NAMES[i].equals(tagName)) {
				return i;
			}
		}
		return -1;
	}

	String getTag(String tagName) {
		int index = getStandardTagIndex(tagName);
		if (index != -1) {
			return standardTags[index];
		} else if (otherTags != null) {
			index = otherTags.indexOf(tagName);
			return (index == -1 ? null : otherTagValues.get(index));
		} else {
			return null;
		}
	}

	void setTag(String tagName, String tagValue) {
		// TN: the dependency between the tags SetUp and FEN is not solved here.
		// So far, it is only treated in PGNWriter::writeHeader.
		int index = getStandardTagIndex(tagName);
		if (index != -1) {
			standardTags[index] = tagValue;
		} else if (!PGN.TAG_PLY_COUNT.equals(tagName)) {
			if (otherTags == null) {
				otherTags = new ArrayList<>();
				otherTagValues = new ArrayList<>();
			}
			index = otherTags.indexOf(tagName);
			if (index == -1) {
				otherTags.add(tagName);
				otherTagValues.add(tagValue); // append
			} else {
				otherTagValues.set(index, tagValue); // replace
			}
		}
	}

	String[] getTags() {
		int numOfTags = (otherTags == null ? 0 : otherTags.size());
		for (int i = 0; i < NUM_OF_STANDARD_TAGS; i++) {
			if (standardTags[i] != null) {
				numOfTags++;
			}
		}

		String[] tags = new String[numOfTags];
		int index = 0;
		for (int i = 0; i < NUM_OF_STANDARD_TAGS; i++) {
			if (standardTags[i] != null) {
				tags[index++] = TAG_NAMES[i];
			}
		}
		if (otherTags != null) {
			for (String otherTag : otherTags) {
				tags[index++] = otherTag;
			}
		}
		return tags;
	}

	String[] getOtherTags() {
		int numOfTags = (otherTags == null ? 0 : otherTags.size());

		if (numOfTags == 0) {
			return null;
		} else {
			String[] tags = new String[numOfTags];
			int index = 0;
			if (otherTags != null) {
				for (String otherTag : otherTags) {
					tags[index++] = otherTag;
				}
			}
			return tags;
		}
	}

	void clearTags() {
		standardTags = new String[NUM_OF_STANDARD_TAGS];
		otherTags = null;
	}

	void removeTag(String tagName) {
		int standardIndex = getStandardTagIndex(tagName);
		if (standardIndex != -1) {
			standardTags[standardIndex] = "";
		} else if (otherTags != null && tagName != null) {
			for (int index = 0; index < otherTags.size(); ++index) {
				if (tagName.equals(otherTags.get(index))) {
					otherTags.remove(index);
					otherTagValues.remove(index);
					--index; // since removeIf is not a choice here
				}
			}
		}
	}

	// =============================================================================

	// convenience methods for tags

	private String getStandardTag(int index) {
		String tag = standardTags[index];
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
		if ("1-0".equals(result)) {
			return Chess.RES_WHITE_WINS;
		} else if ("0-1".equals(result)) {
			return Chess.RES_BLACK_WINS;
		} else if ("1/2-1/2".equals(result)) {
			return Chess.RES_DRAW;
		} else if ("*".equals(result)) {
			return Chess.RES_NOT_FINISHED;
		} else {
			return Chess.NO_RES;
		}
	}

	int getWhiteElo() {
		try {
			String whiteElo = getWhiteEloStr();
			if (whiteElo == null) {
				return 0;
			}
			return Integer.parseInt(whiteElo);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	int getBlackElo() {
		try {
			String blackElo = getBlackEloStr();
			if (blackElo == null) {
				return 0;
			}
			return Integer.parseInt(blackElo);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	// =============================================================================

	boolean contains(GameHeaderModel other) {
		for (int index = 0; index < NUM_OF_STANDARD_TAGS; ++index) {
			String value = other.standardTags[index];
			if (value != null && !value.isEmpty() && !value.equals(standardTags[index])) {
				return false;
			}
		}
		if (other.otherTags == null) {
			return true;
		}
		if (otherTags == null) {
			return other.otherTags.size() == 0;
		}
		for (int index = 0; index < other.otherTags.size(); ++index) {
			String value = other.otherTagValues.get(index);
			if (value != null && !value.isEmpty()) {
				String otherTag = other.otherTags.get(index);
				if (otherTag == null) {
					continue;
				}
				boolean checked = false;
				for (int i = 0; i < otherTags.size(); ++i) {
					String tag = otherTags.get(i);
					if (otherTag.equals(tag)) {
						if (!Objects.equals(other.otherTagValues.get(index), otherTagValues.get(i))) {
							return false;
						}
						checked = true;
						break;
					}
				}
				if (!checked) {
					return false;
				}
			}
		}
		return true;
	}

	// ===================================================================

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

	// =============================================================================

	@Override
	public String toString() {
		return getWhite() + " - " + getBlack() + " " + getResultStr() + " (" + getDate() + ")";
	}
}
