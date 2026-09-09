package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.RepairerSource;

import java.util.List;

// Un annuaire d'où tirer des fiches retoucheurs (SIRENE, CMA, OSM…).
public interface RepairerDirectorySource {

    RepairerSource source();

    List<DirectoryEntry> fetch(ImportCriteria criteria);
}
