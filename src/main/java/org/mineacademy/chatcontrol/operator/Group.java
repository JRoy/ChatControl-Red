package org.mineacademy.chatcontrol.operator;

import java.io.File;

import org.mineacademy.fo.FileUtil;
import org.mineacademy.fo.collection.SerializedMap;

import lombok.Getter;

/**
 * Represents a group holding operators that can be reused by many rules
 */
@Getter
public final class Group extends RuleOperator {

	/**
	 * The name of the group
	 */
	private final String group;

	/**
	 * Create a new rule group by name
	 *
	 * @param group
	 */
	public Group(String group) {
		this.group = group;
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getFile()
	 */
	@Override
	public File getFile() {
		return FileUtil.getFile("rules/groups.rs");
	}

	/**
	 * @see org.mineacademy.fo.model.Rule#getMatch()
	 */
	@Override
	public String getUid() {
		return this.group;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Rule Group " + super.collectOptions().put(SerializedMap.of("Group", this.group)).toStringFormatted();
	}
}
