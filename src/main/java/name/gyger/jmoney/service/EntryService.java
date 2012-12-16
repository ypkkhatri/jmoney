/*
 * Copyright 2012 Johann Gyger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package name.gyger.jmoney.service;

import name.gyger.jmoney.dto.EntryDetailsDto;
import name.gyger.jmoney.dto.EntryDto;
import name.gyger.jmoney.dto.SubEntryDto;
import name.gyger.jmoney.model.Account;
import name.gyger.jmoney.model.Category;
import name.gyger.jmoney.model.CategoryType;
import name.gyger.jmoney.model.Entry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class EntryService {

    @PersistenceContext
    private EntityManager em;

    public long getEntryCount(long accountId) {
        Query q = em.createQuery("SELECT count(e) FROM Entry e WHERE e.account.id = :id");
        q.setParameter("id", accountId);
        return (Long) q.getSingleResult();
    }

    public List<EntryDto> getEntries(long accountId, Integer page, String filter) {
        Query q = em.createQuery("SELECT e FROM Entry e LEFT JOIN FETCH e.category WHERE e.account.id = :id" +
                " ORDER BY CASE WHEN e.date IS NULL THEN 1 ELSE 0 END, e.date, e.creation");
        q.setParameter("id", accountId);

        @SuppressWarnings("unchecked")
        List<Entry> entries = q.getResultList();
        List<EntryDto> result = new ArrayList<EntryDto>();
        EntryDto previousEntryDto = null;
        for (Entry entry : entries) {
            if (entry.contains(filter)) {
                EntryDto entryDto = new EntryDto(entry);
                result.add(entryDto);
                if (previousEntryDto == null) {
                    Account a = entry.getAccount();
                    entryDto.setBalance(entry.getAmount() + a.getStartBalance());
                } else {
                    entryDto.setBalance(entry.getAmount() + previousEntryDto.getBalance());
                }
                previousEntryDto = entryDto;
            }
        }

        Collections.reverse(result);

        if (page == null) {
            page = 1;
        }
        int count = result.size();
        int from = Math.min((page - 1) * 10, count);
        int to = Math.min((page - 1) * 10 + 10, count);
        result = result.subList(from, to);

        return result;
    }

    public EntryDetailsDto getEntry(long id) {
        Entry e = em.find(Entry.class, id);
        return new EntryDetailsDto(e);
    }

    public long createEntry(EntryDetailsDto dto) {
        Entry e = new Entry();
        em.persist(e);
        updateEntryInternal(dto, e);
        return e.getId();
    }

    public void updateEntry(EntryDetailsDto dto) {
        Entry e = em.find(Entry.class, dto.getId());
        updateEntryInternal(dto, e);
    }

    private void updateEntryInternal(EntryDetailsDto dto, Entry e) {
        Account a = em.find(Account.class, dto.getAccountId());
        Category c = em.find(Category.class, dto.getCategoryId());

        if (c instanceof Account) {
            Account otherAccount = (Account) c;

            Entry other = e.getOther();
            if (other == null) {
                other = new Entry();
                em.persist(other);
            }

            other.setOther(e);
            other.setCategory(a);
            other.setAccount(otherAccount);
            e.setOther(other);
        } else {
            Entry other = e.getOther();
            e.setOther(null);

            if (other != null) {
                other.setOther(null);
                em.remove(other);
            }
        }

        removeSubEntries(e);
        if (c != null && c.getType() == CategoryType.SPLIT) {
            createSubEntries(dto, e);
        }

        dto.mapToModel(e);
        e.setCategory(c);
        e.setAccount(a);
    }

    private void removeSubEntries(Entry e) {
        List<Entry> subEntries = e.getSubEntries();
        if (subEntries != null) {
            for (Entry subEntry : subEntries) {
                em.remove(subEntry);
            }
        }
    }

    private void createSubEntries(EntryDetailsDto dto, Entry e) {
        List<SubEntryDto> subEntryDtos = dto.getSubEntries();
        if (subEntryDtos != null) {
            for (SubEntryDto subEntryDto : subEntryDtos) {
                Entry subEntry = new Entry();
                em.persist(subEntry);
                subEntryDto.mapToModel(subEntry);

                Category subCat = em.find(Category.class, subEntryDto.getCategoryId());
                subEntry.setCategory(subCat);
                subEntry.setSplitEntry(e);
            }
        }
    }

    public void deleteEntry(long entryId) {
        Entry e = em.find(Entry.class, entryId);
        em.remove(e);
    }
}
