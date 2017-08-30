/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.db.entity.basic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.room.Room.Right;
import org.apache.openmeetings.db.entity.room.StreamClient;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.wicket.protocol.ws.api.registry.IKey;
import org.apache.wicket.util.string.Strings;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

/**
 * Temporary class, later will be merged with {@link org.apache.openmeetings.db.entity.room.StreamClient}
 * @author solomax
 *
 */
public class Client implements IClient {
	private static final long serialVersionUID = 1L;

	public enum Activity {
		broadcastA //sends Audio to the room
		, broadcastV //sends Video to the room
		, broadcastAV //sends Audio+Video to the room
		, share
		, record
		, publish //sends A/V to external server
		, muted
		, exclusive
	}
	public enum Pod {
		none, left, right;
	}
	public static class Stream implements Serializable {
		private static final long serialVersionUID = 1L;
		private final String streamId;
		private final String uid;
		private final String broadcastId;
		private final Type type;
		private Integer width;
		private Integer height;

		public Stream(String uid, String streamId, String broadcastId, Type type) {
			this.streamId = streamId;
			this.broadcastId = broadcastId;
			this.uid = uid;
			this.type = type;
		}

		public String getStreamId() {
			return streamId;
		}

		public String getBroadcastId() {
			return broadcastId;
		}

		public Type getType() {
			return type;
		}

		public String getUid() {
			return uid;
		}

		public Integer getWidth() {
			return width;
		}

		public void setWidth(Integer width) {
			this.width = width;
		}

		public Integer getHeight() {
			return height;
		}

		public void setHeight(Integer height) {
			this.height = height;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((broadcastId == null) ? 0 : broadcastId.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Stream other = (Stream) obj;
			if (broadcastId == null) {
				if (other.broadcastId != null) {
					return false;
				}
			} else if (!broadcastId.equals(other.broadcastId)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return "Stream [id=" + streamId + ", broadcastId=" + broadcastId + ", type=" + type + "]";
		}
	}
	private final String sessionId;
	private int pageId;
	private User user;
	private Room room;
	private final String uid;
	private final String sid;
	private String remoteAddress;
	private final Set<Right> rights = new HashSet<>();
	private final Set<Activity> activities = new HashSet<>();
	private final Set<Stream> streams = new HashSet<>();
	private final Date connectedSince;
	private Pod pod;
	private int cam = -1;
	private int mic = -1;
	private int width = 0;
	private int height = 0;
	private String serverId = null;
	private Long recordingId;

	public Client(String sessionId, int pageId, Long userId, UserDao dao) {
		this.sessionId = sessionId;
		this.pageId = pageId;
		this.user = dao.get(userId);
		this.connectedSince = new Date();
		uid = UUID.randomUUID().toString();
		sid = UUID.randomUUID().toString();
	}

	public Client(StreamClient rcl, User user) {
		this.sessionId = UUID.randomUUID().toString();
		this.pageId = 0;
		this.user = user;
		this.connectedSince = new Date();
		uid = rcl.getUid();
		sid = rcl.getSid();
		this.remoteAddress = rcl.getRemoteAddress();
	}

	public String getSessionId() {
		return sessionId;
	}

	public int getPageId() {
		return pageId;
	}

	public Client setPageId(IKey key) {
		this.pageId = key.hashCode();
		return this;
	}

	public void setPageId(int pageId) {
		this.pageId = pageId;
	}

	public User getUser() {
		return user;
	}

	public Client updateUser(UserDao dao) {
		user = dao.get(user.getId());
		return this;
	}

	@Override
	public Long getUserId() {
		return user.getId();
	}

	@Override
	public String getLogin() {
		return user.getLogin();
	}

	@Override
	public String getUid() {
		return uid;
	}

	@Override
	public String getSid() {
		return sid;
	}

	public void clear() {
		activities.clear();
		rights.clear();
		streams.clear();
	}

	public boolean hasRight(Right right) {
		if (Right.superModerator == right) {
			return rights.contains(right);
		}
		return rights.contains(Right.superModerator) || rights.contains(Right.moderator) ? true : rights.contains(right);
	}

	public Client allow(Right... _rights) {
		allow(Arrays.asList(_rights));
		return this;
	}

	public void allow(Iterable<Right> _rights) {
		for (Right right : _rights) {
			if (!hasRight(right)) {
				rights.add(right);
			}
		}
	}

	public void deny(Right... _rights) {
		for (Right right : _rights) {
			rights.remove(right);
		}
	}

	public void clearActivities() {
		activities.clear();
	}

	public boolean hasAnyActivity(Activity... aa) {
		boolean res = false;
		if (aa != null) {
			for (Activity a : aa) {
				res |= activities.contains(a);
			}
		}
		return res;
	}

	public boolean hasActivity(Activity a) {
		return activities.contains(a);
	}

	public void toggle(Activity a) {
		if (hasActivity(a)) {
			remove(a);
		} else {
			set(a);
		}
	}

	public Client set(Activity a) {
		activities.add(a);
		switch (a) {
			case broadcastV:
			case broadcastA:
				if (hasActivity(Activity.broadcastA) && hasActivity(Activity.broadcastV)) {
					activities.add(Activity.broadcastAV);
				}
				break;
			case broadcastAV:
				activities.add(Activity.broadcastA);
				activities.add(Activity.broadcastV);
				break;
			default:
		}
		return this;
	}

	public Client remove(Activity a) {
		activities.remove(a);
		switch (a) {
			case broadcastV:
			case broadcastA:
				activities.remove(Activity.broadcastAV);
				break;
			case broadcastAV:
				activities.remove(Activity.broadcastA);
				activities.remove(Activity.broadcastV);
				break;
			case share:
				for (Stream s : streams) {
					if (Type.sharing == s.getType()) {
						streams.remove(s);
						break;
					}
				}
				break;
			default:
		}
		return this;
	}

	public Client addStream(String uid, String streamId, String broadcastId, Type type) {
		if (broadcastId != null) {
			streams.add(new Stream(uid, streamId, broadcastId, type));
		}
		return this;
	}

	public Client removeStream(String broadcastId) {
		if (broadcastId != null) {
			streams.remove(new Stream(null, null, broadcastId, Type.video));
		}
		return this;
	}

	public List<Stream> getStreams() {
		return new ArrayList<>(streams);
	}

	public Date getConnectedSince() {
		return connectedSince;
	}

	@Override
	public Long getId() {
		return null;
	}

	@Override
	public void setId(Long id) {
	}

	public Room getRoom() {
		return room;
	}

	public Client setRoom(Room room) {
		this.room = room;
		return this;
	}

	public Pod getPod() {
		return pod;
	}

	public void setPod(Pod pod) {
		this.pod = pod;
	}

	public boolean isCamEnabled() {
		return cam > -1;
	}

	public int getCam() {
		return cam;
	}

	public Client setCam(int cam) {
		this.cam = cam;
		return this;
	}

	public boolean isMicEnabled() {
		return mic > -1;
	}

	public int getMic() {
		return mic;
	}

	public Client setMic(int mic) {
		this.mic = mic;
		return this;
	}

	@Override
	public int getWidth() {
		return width;
	}

	public Client setWidth(int width) {
		this.width = width;
		return this;
	}

	@Override
	public int getHeight() {
		return height;
	}

	public Client setHeight(int height) {
		this.height = height;
		return this;
	}

	@Override
	public String getRemoteAddress() {
		return remoteAddress;
	}

	public Client setRemoteAddress(String remoteAddress) {
		this.remoteAddress = remoteAddress;
		return this;
	}

	@Override
	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	@Override
	public void setRecordingStarted(boolean recordingStarted) {
		if (recordingStarted) {
			activities.add(Activity.record);
		} else {
			activities.remove(Activity.record);
		}
	}

	@Override
	public Long getRecordingId() {
		return recordingId;
	}

	@Override
	public void setRecordingId(Long recordingId) {
		this.recordingId = recordingId;
	}

	@Override
	public Long getRoomId() {
		return room == null ? null : room.getId();
	}

	public JSONObject toJson(boolean self) {
		JSONObject u = new JSONObject();
		if (user != null) {
			JSONObject a = new JSONObject();
			u.put("id", user.getId())
				.put("firstName", user.getFirstname())
				.put("lastName", user.getLastname())
				.put("address", a);
			if (user.getAddress() != null) {
				if (Strings.isEmpty(user.getFirstname()) && Strings.isEmpty(user.getLastname())) {
					a.put("email", user.getAddress().getEmail());
				}
				a.put("country", user.getAddress().getCountry());
			}
		}
		JSONObject json = new JSONObject()
				.put("user", u)
				.put("uid", uid)
				.put("rights", new JSONArray(rights))
				.put("activities", new JSONArray(activities))
				.put("pod", pod)
				.put("width", width)
				.put("height", height)
				.put("self", self);
		if (self) {
			json.put("cam", cam).put("mic", mic);
		}
		return json;
	}

	public JSONArray streamArray(boolean self) {
		// stream `uid` is unknown at the time of self stream creation
		// so we will replace stream `uid` with client `uid` for self
		return new JSONArray(streams.stream().map(
				s -> self && Type.room == s.getType() ? uid : s.getUid()
			).collect(Collectors.toList()));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uid == null) ? 0 : uid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Client)) {
			return false;
		}
		Client other = (Client) obj;
		if (uid == null) {
			if (other.uid != null) {
				return false;
			}
		} else if (!uid.equals(other.uid)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "Client [uid=" + uid + ", sessionId=" + sessionId + ", pageId=" + pageId + ", userId=" + user.getId() + ", room=" + (room == null ? null : room.getId())
				+ ", rights=" + rights + ", activities=" + activities + ", connectedSince=" + connectedSince + ", pod = " + pod + "]";
	}
}
