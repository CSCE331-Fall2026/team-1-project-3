#include <openssl/evp.h>
#include <openssl/rand.h>

#include <array>
#include <climits>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

// AES-256-GCM (AI Generated)
namespace crypto
{
    constexpr std::size_t KeySize = 32; // AES-256 key: 32 bytes
    constexpr std::size_t IvSize = 12;  // Recommended GCM nonce size
    constexpr std::size_t TagSize = 16;
    constexpr unsigned char FormatVersion = 1;

    std::string encryptString(
        std::string_view plaintext,
        const std::array<unsigned char, KeySize>& key)
    {
        if (plaintext.size() > static_cast<std::size_t>(INT_MAX))
        {
            throw std::runtime_error("Plaintext is too large.");
        }

        std::array<unsigned char, IvSize> iv {};
        if (RAND_bytes(iv.data(), static_cast<int>(iv.size())) != 1)
        {
            throw std::runtime_error("Could not generate a random IV.");
        }

        using CipherContextPtr =
            std::unique_ptr<EVP_CIPHER_CTX, decltype(&EVP_CIPHER_CTX_free)>;

        CipherContextPtr context(EVP_CIPHER_CTX_new(), EVP_CIPHER_CTX_free);
        if (!context)
        {
            throw std::runtime_error("Could not create encryption context.");
        }

        if (EVP_EncryptInit_ex(context.get(), EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1 ||
            EVP_CIPHER_CTX_ctrl(
                context.get(),
                EVP_CTRL_GCM_SET_IVLEN,
                static_cast<int>(iv.size()),
                nullptr) != 1 ||
            EVP_EncryptInit_ex(context.get(), nullptr, nullptr, key.data(), iv.data()) != 1)
        {
            throw std::runtime_error("Could not initialize AES-256-GCM.");
        }

        std::vector<unsigned char> ciphertext(plaintext.size() + EVP_MAX_BLOCK_LENGTH);

        int ciphertextLength = 0;
        if (EVP_EncryptUpdate(
                context.get(),
                ciphertext.data(),
                &ciphertextLength,
                reinterpret_cast<const unsigned char*>(plaintext.data()),
                static_cast<int>(plaintext.size())) != 1)
        {
            throw std::runtime_error("Encryption failed.");
        }

        int finalLength = 0;
        if (EVP_EncryptFinal_ex(
                context.get(),
                ciphertext.data() + ciphertextLength,
                &finalLength) != 1)
        {
            throw std::runtime_error("Could not finalize encryption.");
        }

        ciphertext.resize(
            static_cast<std::size_t>(ciphertextLength + finalLength));

        std::array<unsigned char, TagSize> tag {};
        if (EVP_CIPHER_CTX_ctrl(
                context.get(),
                EVP_CTRL_GCM_GET_TAG,
                static_cast<int>(tag.size()),
                tag.data()) != 1)
        {
            throw std::runtime_error("Could not retrieve authentication tag.");
        }

        // Binary layout:
        // [1-byte version][12-byte IV][ciphertext][16-byte authentication tag]
        std::vector<unsigned char> encrypted;
        encrypted.reserve(1 + iv.size() + ciphertext.size() + tag.size());

        encrypted.push_back(FormatVersion);
        encrypted.insert(encrypted.end(), iv.begin(), iv.end());
        encrypted.insert(encrypted.end(), ciphertext.begin(), ciphertext.end());
        encrypted.insert(encrypted.end(), tag.begin(), tag.end());

        // Encode binary data as Base64 so it can be stored as a std::string.
        const std::size_t base64Length = 4 * ((encrypted.size() + 2) / 3);
        std::string result(base64Length, '\0');

        const int written = EVP_EncodeBlock(
            reinterpret_cast<unsigned char*>(result.data()),
            encrypted.data(),
            static_cast<int>(encrypted.size()));

        if (written < 0)
        {
            throw std::runtime_error("Base64 encoding failed.");
        }

        result.resize(static_cast<std::size_t>(written));
        return result;
    }
}