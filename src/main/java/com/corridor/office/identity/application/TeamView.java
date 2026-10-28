package com.corridor.office.identity.application;

import java.util.UUID;

public record TeamView(UUID id, String name, String color, long memberCount, long version) {}
