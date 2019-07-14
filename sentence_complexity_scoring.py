import numpy as np
import pandas as pd
import string
import json
import sys

# https://github.com/craklyn/assisted-thriving

df = pd.read_csv("lexicon.tsv", sep='\t', names=["word", "complexity"])
df['word_lower'] = df["word"].str.lower()


def vocab_level(sentence):
    word_complexity_scores = [df[df['word_lower'] == word.lower()]["complexity"].iloc[0] if any(word.lower() == df["word_lower"]) else 5.0 for word in sentence.split()]
    return {"word_complexity_scores": word_complexity_scores,
            "total_score": np.nansum(word_complexity_scores) / len(word_complexity_scores)}


# This function is useless.  Don't use it.
def potential_vocabulary_size(sentences):
    words = [word.lower() for sentence in sentences for word in sentence.split()]
    known_words = set([word for word in words if any(word == df["word_lower"])])

    N = len(words)
    print(df.size)
    n = df.shape[0]

    numer = 0
    for i in range(len(known_words)):
        numer += 1./(i+1)

    denom = 0
    for i in range(df.shape[0]):
        denom += (1. / (i+1))

    print("Numer: " + str(numer))
    print("Denom: " + str(denom))

    return numer / denom


if __name__ == "__main__":
    input_text = sys.stdin.readline()
    # for line in sys.stdin:
    #     input_text += " " + line

    cleaned_text = input_text.strip().translate(str.maketrans('', '', string.punctuation))
    sentence_score = vocab_level(cleaned_text)
    sentence_score['eval_text'] = cleaned_text

    print(json.dumps(sentence_score))