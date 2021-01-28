# lid-poc-android
End-to-end Language Identification Proof of Concept on Android.

This is a POC of an end-to-end language identification CRNN model working on Android. The model's implementation is heavily based on the paper [Bartz, Christian, et al. “Language Identification Using Deep Convolutional Recurrent Neural Networks.”](https://arxiv.org/abs/1708.04811)<sup>[1](#paperlink)</sup>. It's trained on a variety of data sources, including [CallHome](https://doi.org/10.35111/exq3-x930), [Switchboard](https://doi.org/10.35111/sw3h-rw02), [JSUT](https://arxiv.org/abs/1711.00354), [HKUST/MTS](https://doi.org/10.1007/11939993_73), and others. Due to the huge variability among the training dataset, both in terms of quality and quantity, the end recognition result is not ideal (about 70% accuracy from initial testing on outside data), with better datasets, one theoretically should achieve much better results.

The Android implementation uses TensorFlow Lite.
 
<a name="paperlink">1</a>: Bartz, Christian, et al. “Language Identification Using Deep Convolutional Recurrent Neural Networks.” Neural Information Processing, edited by Derong Liu et al., Springer International Publishing, 2017, pp. 880–89. Springer Link, doi:10.1007/978-3-319-70136-3_93.
