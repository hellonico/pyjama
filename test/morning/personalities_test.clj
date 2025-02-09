(ns morning.personalities-test
  (:require [clojure.test :refer :all]
            [pyjama.personalities]))

(deftest japanese-translation
  (->
    (pyjama.personalities/japanese-translator
      "これから成田空港に行って、飛行機に乗って、ソウルに行きます。")
    println))

(def napoleon-text
  "Napoleon Bonaparte[b] (born Napoleone di Buonaparte;[1][c] 15 August 1769 – 5 May 1821), later known by his regnal name Napoleon I, was a French military officer and statesman who rose to prominence during the French Revolution and led a series of successful campaigns across Europe during the French Revolutionary and Napoleonic Wars from 1796 to 1815. He was the leader of the French Republic as First Consul from 1799 to 1804, then of the French Empire as Emperor of the French from 1804 to 1814, and briefly again in 1815.\n\nBorn on the island of Corsica to a family of Italian origin, Napoleon moved to mainland France in 1779 and was commissioned as an officer in the French Royal Army in 1785. He supported the French Revolution in 1789, and promoted its cause in Corsica. He rose rapidly through the ranks after winning the siege of Toulon in 1793 and defeating royalist insurgents in Paris on 13 Vendémiaire in 1795. In 1796, Napoleon commanded a military campaign against the Austrians and their Italian allies in the War of the First Coalition, scoring decisive victories and becoming a national hero. He led an invasion of Egypt and Syria in 1798 which served as a springboard to political power. In November 1799, Napoleon engineered the Coup of 18 Brumaire against the Directory, and became First Consul of the Republic. He won the Battle of Marengo in 1800, which secured France's victory in the War of the Second Coalition, and in 1803 sold the territory of Louisiana to the United States. In December 1804, Napoleon crowned himself Emperor of the French, further expanding his power.\n\nThe breakdown of the Treaty of Amiens led to the War of the Third Coalition by 1805. Napoleon shattered the coalition with a decisive victory at the Battle of Austerlitz, which led to the dissolution of the Holy Roman Empire. In the War of the Fourth Coalition, Napoleon defeated Prussia at the Battle of Jena–Auerstedt in 1806, marched his Grande Armée into Eastern Europe, and defeated the Russians in 1807 at the Battle of Friedland. Seeking to extend his trade embargo against Britain, Napoleon invaded the Iberian Peninsula and installed his brother Joseph as King of Spain in 1808, provoking the Peninsular War. In 1809, the Austrians again challenged France in the War of the Fifth Coalition, in which Napoleon solidified his grip over Europe after winning the Battle of Wagram. In summer 1812, he launched an invasion of Russia, which ended in the catastrophic retreat of his army that winter. In 1813, Prussia and Austria joined Russia in the War of the Sixth Coalition, in which Napoleon was decisively defeated at the Battle of Leipzig. The coalition invaded France and captured Paris, forcing Napoleon to abdicate in April 1814. They exiled him to the Mediterranean island of Elba and restored the Bourbons to power. Ten months later, Napoleon escaped from Elba on a brig, landed in France with a thousand men, and marched on Paris, again taking control of the country. His opponents responded by forming a Seventh Coalition, which defeated him at the Battle of Waterloo in June 1815. Napoleon was exiled to the remote island of Saint Helena in the South Atlantic, where he died of stomach cancer in 1821, aged 51.\n\nNapoleon is considered one of the greatest military commanders in history and Napoleonic tactics are still studied at military schools worldwide. His legacy endures through the modernizing legal and administrative reforms he enacted in France and Western Europe, embodied in the Napoleonic Code. He established a system of public education,[2] abolished the vestiges of feudalism,[3] emancipated Jews and other religious minorities,[4] abolished the Spanish Inquisition,[5] enacted the principle of equality before the law for an emerging middle class,[6] and centralized state power at the expense of religious authorities.[7] His conquests acted as a catalyst for political change and the development of nation states. However, he is controversial due to his role in wars which devastated Europe, his looting of conquered territories, and his mixed record on civil rights. He abolished the free press, ended directly elected representative government, exiled and jailed critics of his regime, reinstated slavery in France's colonies except for Haiti, banned the entry of blacks and mulattos into France, reduced the civil rights of women and children in France, reintroduced a hereditary monarchy and nobility,[8][9][10] and violently repressed popular uprisings against his rule.[11] ")

(deftest samuraizer
  (pyjama.personalities/samuraizer
    {:prompt
     napoleon-text
     :stream true}))

(deftest three-points
  (pyjama.personalities/three-points
    {:prompt napoleon-text
     :stream true}))

(deftest dad
  (->
    (pyjama.personalities/dad
      {:prompt
       [napoleon-text "Who was Napoleon?"]
       :pre    "This is your knowledge: %s. Answer the question %s"
       :stream false})
    println))
