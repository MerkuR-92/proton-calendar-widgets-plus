package me.proton.android.calendar.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.proton.android.calendar.data.db.AppDatabase

data class AddressKey(
    override val id: String,
    val version: Int, // TODO how to handle this?
    val primary: Int, // 1 -- primary
    val flags: Int,
    val privateKey: String,
    val publicKey: String
//    val token: String?,
//    val signature: String?
//    val activation: String?

    /**
     * {
    "ID": "DvRZxrFRFUnjsL6MCOdGjyMZ9AoECd7kNXX9uKxmV-75K4iArEHijRPvd7Dhw43yBlCDxIsbNSW7cg2Uu5FUFg==",
    "Primary": 1,
    "Flags": 3,
    "Fingerprint": "20cf363b58ec99e722e53ec411c31e8e5e07f4d0", -------------> DEPRECATED
    "Fingerprints": [ -------------> DEPRECATED
    "20cf363b58ec99e722e53ec411c31e8e5e07f4d0",
    "781d2a320fe48f622664757d991a8ec5040999a0"
    ],
    "PublicKey": "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: ProtonMail\n\nxsBNBF1BfxUBCADUpiiG3AhQK08E2nBmQ50XeztOWArmknINQV41pqGFW5VQ\nkfbQ3FYsANhLGqbDBQ0XxmocjKL7W7W8Y4xmHCGgkCUy6gAqGbi+sXY9Sl8x\nqQNHuZDhWVdqT8+Rtv+DRxp/XrGkzC1U8CBYUmmKS92ldy0/zZIvgQXT6t5Q\n+v+BeUSv4jCsnY3BE0UBOljtrTXlOcXRZHQxORWG+kon0qgcJERdwwzhxY6e\nT8jEfAfJY0hzQaYg+6bj6ZR0zkMtY2Psq2M05kzEw4On/dezZETAu1e9fSqf\nk1mp+H6BeLJ9RUyrFK/PqIO48+pU8CmAvTdx5eIihyOM16CFg/3GgV85ABEB\nAAHNMWFkYW10c3RAcHJvdG9ubWFpbC5ibHVlIDxhZGFtdHN0QHByb3Rvbm1h\naWwuYmx1ZT7CwHIEEwEIABwFAl1BfxUJEBHDHo5eB/TQAhsDAhkBAgsJAhUI\nAAoJEBHDHo5eB/TQx6IIAAg2A2ZMkzGV+vZPbqAMoAEO+dpG+dq9C93Ui4Hv\noVHpcSTolVM522r81Yc48xdhbnFz9HLDkicoBzXo40utgQ7bF4iKD4lQztfh\n6+9l+IBNu+1XmdW+laMybygtPh+H4YPxLZA9O6FYRyUcTjlZYFFxipz9pc9q\nI58tDHIILzfjZPCC6reiJpbxJOgp07PV3ZnJqLDIkFPlPkxyqymfuWHnPOJM\n5RxvHnu04ptsp/Z/xbgUra2JEyVLA7gC/yznxfQ58087pCupKqQwepA3zHmE\nCS6vk7uuNp++D9JajjtFsu4piP4cTNVvMqnDXWn0uzwrhhw/fZnnHSllXmBw\ngmPOwE0EXUF/FQEIAMgCI+srSwdQlIpz+n+mlSpS0jPXvRYoL9QgMOdzR3kA\nW5sM1OW2Z7ROlBEZ7ycurpe4Sa/SaKfjtf4wOs2hmpxecL9JxL0x3KGEaSeE\nIiYIkMb4TnSLR9vfowVdReOMTs5RpxMxQL+xmz3nChwLEIF/amAo/ucnXLbU\nNvYFkOpzdtxtN0dy2ykUvR9rsNUiGBoIn/BYCqSXpsCY7kom8lYl039yQvGV\nLWG6vryF6gExRbW61B3yjACpR6NLi2Bqta0SDRkeg5obumxoWaJ7ltJ2uPuV\nofOpIPXP2CO40iCLKUUZB/r+/kVx+dYfEW3Nk4r+uKsu3CCSB9AZNJRQGiEA\nEQEAAcLAaQQYAQgAEwUCXUF/FQkQEcMejl4H9NACGwwACgkQEcMejl4H9NCC\n8AgAbItodhOOJcb85EggCB1CEoFg6jOs5LgRw4810xI8HBPo/4Gk1L8YPfen\nMA1Uoz0x+3z42d49QU5HZ/hAmtDVW9KP2Sjw/axfsgB7v6sbrXgtB/OMblHX\noqVJU4wVbQrYvxnG6YN1iX83QGGC1mYHWWDXFjZM8egN63Ocyccbywvq7q/K\nEaXlrqpxbaDW6uUXRUX8ISqDWXAAqEUcgWI1H5fqMKODQolr0yMBbqggI7Gh\nfSOnX3mZaLHqy5ElJZUrXi6J5Pq4vnJgLm1kzP632uztjEKQfEVFPUflksdQ\nP+v3eWKpb6nNTH5tV3Pmo0xvRmicdlEt7f8XNvX3HxQw9w==\n=E6ZL\n-----END PGP PUBLIC KEY BLOCK-----\n",
    "Version": 3,
    "Activation": null,
    "PrivateKey": "-----BEGIN PGP PRIVATE KEY BLOCK-----\nVersion: ProtonMail\n\n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\ndlEt7f8XNvX3HxQw9w==\n=FW0u\n-----END PGP PRIVATE KEY BLOCK-----\n",
    "Token": null,
    "Signature": null
    }
     */
): BaseModel()

