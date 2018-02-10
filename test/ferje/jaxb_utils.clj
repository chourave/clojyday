(ns ferje.jaxb-utils)


(defn set-jaxb-context-factory [factory]
  "Set the context factory for JAXB (used by Jollyday)"
  (System/setProperty "javax.xml.bind.JAXBContextFactory" factory))

(defn jaxb-fixture
  "Init JAXB with Eclipse implementation
  (because the one shipping with the JDK is being deprecated)"
  [f]
  (set-jaxb-context-factory "org.eclipse.persistence.jaxb.JAXBContextFactory")
  (f))
