package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.ArtisanSource;

import java.util.List;

// Un annuaire d'où tirer des fiches artisans (SIRENE…).
public interface ArtisanDirectorySource {

    ArtisanSource source();

    List<DirectoryEntry> fetch(ImportCriteria criteria);
}
