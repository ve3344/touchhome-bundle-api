package org.touchhome.bundle.api.repository;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class AbstractRepository<T extends BaseEntity> implements PureRepository<T> {

    private final Class<T> clazz;
    private final String prefix;

    @PersistenceContext
    protected EntityManager em;

    public AbstractRepository(Class<T> clazz) {
        this.clazz = clazz;
        this.prefix = Modifier.isAbstract(clazz.getModifiers()) ? null : TouchHomeUtils.newInstance(clazz).getEntityPrefix();
    }

    @Transactional(readOnly = true)
    public T getByName(String name) {
        return findSingleByField("name", name);
    }

    /**
     * Warning!!!
     * Must be called only from EntityManager. That's why it's not transactional
     */
    public T save(T entity) {
        entity.getEntityID(true); // verify entityID exists
        return em.merge(entity);
    }

    @Transactional
    @Override
    public void flushCashedEntity(T entity) {
        em.merge(entity);
    }

    @Transactional(readOnly = true)
    public List<T> listAll() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(getEntityClass());
        Root<T> rootEntry = cq.from(getEntityClass());
        CriteriaQuery<T> all = cq.select(rootEntry);
        TypedQuery<T> allQuery = em.createQuery(all);
        return allQuery.getResultList();
    }

    @Transactional(readOnly = true)
    public T getByEntityID(String entityID) {
        return findSingleByField("entityID", entityID);
    }

    @Transactional(readOnly = true)
    public boolean isExistsByEntityID(String entityID) {
        return findSingleByField("entityID", entityID) != null;
    }

    protected List<T> findByField(String fieldName, Object value) {
        return em.createQuery("FROM " + getEntityClass().getSimpleName() + " where " + fieldName + " = :value", getEntityClass())
                .setParameter("value", value).getResultList();
    }

    protected List<T> findByFieldRange(String fieldName, Object... values) {
        String inStatement = Stream.of(values).map(Object::toString).collect(Collectors.joining(",", "'", "'"));
        TypedQuery<T> query = em.createQuery("FROM " + getEntityClass().getSimpleName() + " where " + fieldName + " in (:value)", getEntityClass())
                .setParameter("value", inStatement);
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public Long size() {
        return em.createQuery("SELECT count(t.id) FROM " + getEntityClass().getSimpleName() + " as t", Long.class).getSingleResult();
    }

    @Transactional(readOnly = true)
    protected T findSingleByField(String fieldName, Object value) {
        return findByField(fieldName, value).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public T getByEntityIDWithFetchLazy(String entityID, boolean ignoreNotUILazy) {
        T byEntityID = getByEntityID(entityID);
        if (byEntityID != null) {
            fetchLazy(byEntityID, new HashSet<>(), ignoreNotUILazy);
        }
        return byEntityID;
    }

    private void fetchLazy(Object entity, Set<Object> visitedEntities, boolean ignoreNotUI) {
        FieldUtils.getAllFieldsList(entity.getClass()).forEach(field -> {
            try {
                if (ignoreNotUI && field.getAnnotation(UIField.class) == null) {
                    return;
                }
                if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(OneToOne.class) || field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(ManyToMany.class)) {
                    Object proxy = FieldUtils.readField(field, entity, true);
                    Hibernate.initialize(proxy);
                    if (proxy != null && visitedEntities.add(proxy)) {
                        if (proxy instanceof Collection) {
                            ((Collection<?>) proxy).forEach(o -> fetchLazy(o, visitedEntities, ignoreNotUI));
                        } else {
                            fetchLazy(proxy, visitedEntities, ignoreNotUI);
                        }
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public T deleteByEntityID(String entityID) {
        T entity = getByEntityID(entityID);
        if (entity != null) {
            em.remove(entity);
            log.warn("Entity <{}> was removed", entity);
        }

        return entity;
    }

    @Override
    public Class<T> getEntityClass() {
        return clazz;
    }

    public boolean isMatch(String entityID) {
        return prefix != null && entityID.startsWith(prefix);
    }
}
